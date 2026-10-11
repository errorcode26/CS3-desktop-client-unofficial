package com.lagradost.runtime.dex;

import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.ir.Trap;
import com.googlecode.dex2jar.ir.expr.Local;
import com.googlecode.dex2jar.ir.expr.RefExpr;
import com.googlecode.dex2jar.ir.expr.Value;
import com.googlecode.dex2jar.ir.expr.Value.VT;
import com.googlecode.dex2jar.ir.stmt.LabelStmt;
import com.googlecode.dex2jar.ir.stmt.Stmt;
import com.googlecode.dex2jar.ir.stmt.Stmt.ST;
import com.googlecode.dex2jar.ir.ts.Cfg;
import com.googlecode.dex2jar.ir.ts.Transformer;
import com.lagradost.runtime.dex.Liveness.Node;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drop-in replacement for dex2jar's Ir2JRegAssignTransformer (Apache 2.0, Panxiaobo) that keeps the
 * interference graph and liveness in bit sets instead of per statement object frames.
 * The coloring is the same algorithm: locals of the same type share JVM slots when they never
 * interfere, "this" and parameters keep their slots and long/double take two slots.
 * <p>
 * The aggressive mode additionally coalesces copies (so they compile to nothing), for methods that
 * are over the JVM's 64KB code limit with the regular allocation: Kotlin coroutine state machines
 * restore dozens of locals at every suspension point, and SSA destruction turns each of those into
 * copies that the regular greedy coloring mostly keeps.
 */
public class LowMemRegAssignTransformer implements Transformer {

    private static final class Reg {
        final int index;
        BitSet excludes;
        Set<Reg> prefers = new HashSet<>(3);
        int reg = -1;
        char type;
        int excludeCount;
        /** aggressive mode: the locals merged into this one (null = only itself) */
        BitSet members;

        Reg(int index) {
            this.index = index;
        }
    }

    /**
     * false: exactly dex2jar's graph and coloring.
     * true: aggressive copy coalescing. Copies don't make their operands interfere (Chaitin's rule)
     * and copy related locals are merged before coloring. Locals defined inside a try range also
     * interfere with everything live at its handlers, because the JVM's inference verifier merges the
     * types of every instruction of a protected range into the handler (not only throwing ones).
     */
    private final boolean aggressive;

    public LowMemRegAssignTransformer() {
        this(false);
    }

    public LowMemRegAssignTransformer(boolean aggressive) {
        this.aggressive = aggressive;
    }

    private static final Comparator<Reg> ORDER_REG_ASSIGN_BY_PREFERRED_SIZE_DESC = (o1, o2) -> {
        int x = o2.prefers.size() - o1.prefers.size();
        if (x == 0) {
            x = o2.excludeCount - o1.excludeCount;
        }
        return x;
    };

    private static final Comparator<Reg> ORDER_BY_EXCLUDES_DESC = (o1, o2) -> o2.excludeCount - o1.excludeCount;

    @Override
    public void transform(IrMethod method) {
        if (method.locals.isEmpty()) {
            return;
        }
        int localCount = Cfg.reIndexLocal(method);
        Cfg.createCFG(method);
        List<Node> created = Liveness.buildTree(method, null);
        Liveness live = Liveness.index(method, localCount, created);
        live.solve();

        final Reg[] regs = new Reg[localCount];
        for (Local local : method.locals) {
            Reg reg = new Reg(local.lsIndex);
            char type = local.valueType.charAt(0);
            if (type == '[') {
                type = 'L';
            }
            reg.type = type;
            reg.excludes = new BitSet(localCount);
            regs[local.lsIndex] = reg;
        }

        Reg[] args = genGraph(method, regs, live);
        if (aggressive) {
            addTrapEdges(method, regs, live);
        }

        // @this never shares its slot
        if (!method.isStatic && args[0] != null) {
            Reg atThis = args[0];
            for (Reg reg : regs) {
                if (reg == atThis) {
                    continue;
                }
                reg.excludes.set(atThis.index);
                atThis.excludes.set(reg.index);
            }
        }

        { // assign @this, @parameter_x from index 0
            int i = 0;
            int index = 0;
            if (!method.isStatic) {
                Reg r = args[i++];
                if (r != null) {
                    r.reg = index;
                }
                index++;
            }
            for (int j = 0; j < method.args.length; j++) {
                Reg reg = args[i++];
                String type = method.args[j];
                if (reg != null) {
                    reg.reg = index;
                }
                index++;
                if ("J".equals(type) || "D".equals(type)) {
                    index++;
                }
            }
        }

        Map<Character, List<Reg>> groups = groupAndCleanUpByType(regs, localCount);
        if (aggressive) {
            coalesce(method, regs, groups);
        }
        BitSet excludeColor = new BitSet();
        BitSet suggestColor = new BitSet();
        BitSet globalExcludes = new BitSet();
        BitSet usedInOneType = new BitSet();
        for (Map.Entry<Character, List<Reg>> e : groups.entrySet()) {
            List<Reg> assigns = e.getValue();
            assigns.sort(aggressive ? ORDER_BY_EXCLUDES_DESC : ORDER_REG_ASSIGN_BY_PREFERRED_SIZE_DESC);
            char type = e.getKey();
            boolean doubleOrLong = type == 'J' || type == 'D';
            for (Reg as : assigns) {
                if (as.reg < 0) {
                    excludeColor.clear();
                    for (int x = as.excludes.nextSetBit(0); x >= 0; x = as.excludes.nextSetBit(x + 1)) {
                        Reg ex = regs[x];
                        if (ex.reg >= 0) {
                            excludeColor.set(ex.reg);
                            if (ex.type == 'J' || ex.type == 'D') {
                                excludeColor.set(ex.reg + 1);
                            }
                        }
                    }
                    for (Reg arg : args) {
                        if (arg != null && arg.type != type && arg.reg >= 0) {
                            excludeColor.set(arg.reg);
                            if (arg.type == 'J' || arg.type == 'D') {
                                excludeColor.set(arg.reg + 1);
                            }
                        }
                    }
                    excludeColor.or(globalExcludes);

                    suggestColor.clear();
                    for (Reg p : as.prefers) {
                        if (p.reg >= 0) {
                            suggestColor.set(p.reg);
                        }
                    }
                    for (int i = suggestColor.nextSetBit(0); i >= 0; i = suggestColor.nextSetBit(i + 1)) {
                        if (doubleOrLong) {
                            if (!excludeColor.get(i) && !excludeColor.get(i + 1)) {
                                as.reg = i;
                                break;
                            }
                        } else {
                            if (!excludeColor.get(i)) {
                                as.reg = i;
                                break;
                            }
                        }
                    }
                    if (as.reg < 0) {
                        if (doubleOrLong) {
                            int reg = -1;
                            do {
                                reg++;
                                reg = excludeColor.nextClearBit(reg);
                            } while (excludeColor.get(reg + 1));
                            as.reg = reg;
                        } else {
                            as.reg = excludeColor.nextClearBit(0);
                        }
                    }
                    if (as.members != null) {
                        for (int m = as.members.nextSetBit(0); m >= 0; m = as.members.nextSetBit(m + 1)) {
                            regs[m].reg = as.reg;
                        }
                    }
                }
                usedInOneType.set(as.reg);
                if (doubleOrLong) {
                    usedInOneType.set(as.reg + 1);
                }
            }
            globalExcludes.or(usedInOneType);
            usedInOneType.clear();
        }

        for (Local local : method.locals) {
            local.lsIndex = regs[local.lsIndex].reg;
            local.tag = null;
        }
        for (Stmt stmt : method.stmts) {
            stmt.frame = null;
        }
    }


    private Reg[] genGraph(IrMethod method, final Reg[] regs, Liveness live) {
        Reg[] args = method.isStatic ? new Reg[method.args.length] : new Reg[method.args.length + 1];
        Set<Stmt> tos = new HashSet<>();
        BitSet edges = new BitSet(regs.length);
        for (Stmt stmt : method.stmts) {
            if ((stmt.st == ST.ASSIGN || stmt.st == ST.IDENTITY) && stmt.getOp1().vt == VT.LOCAL) {
                Local left = (Local) stmt.getOp1();
                Value op2 = stmt.getOp2();
                int idx = left.lsIndex;
                Reg leftReg = regs[idx];

                // a new local can't share a slot with anything live after it
                Cfg.collectTos(stmt, tos);
                edges.clear();
                for (Stmt next : tos) {
                    BitSet in = live.liveIn(next);
                    if (in != null) {
                        edges.or(in);
                    }
                }
                tos.clear();
                edges.clear(idx);
                if (aggressive && op2.vt == VT.LOCAL) {
                    // Chaitin's rule: "a = b" doesn't make a and b interfere, they hold the same value.
                    // Any other definition of either while the other is live still adds the edge.
                    edges.clear(((Local) op2).lsIndex);
                }
                addEdges(regs, idx, edges);

                if (op2.vt == VT.LOCAL) {
                    Reg rightReg = regs[((Local) op2).lsIndex];
                    leftReg.prefers.add(rightReg);
                    rightReg.prefers.add(leftReg);
                }

                if (op2.vt == VT.THIS_REF) {
                    args[0] = leftReg;
                } else if (op2.vt == VT.PARAMETER_REF) {
                    RefExpr refExpr = (RefExpr) op2;
                    if (method.isStatic) {
                        args[refExpr.parameterIndex] = leftReg;
                    } else {
                        args[refExpr.parameterIndex + 1] = leftReg;
                    }
                }
            }
        }
        for (Reg reg : regs) {
            reg.excludes.clear(reg.index);
            reg.prefers.remove(reg);
        }
        return args;
    }

    private static void addEdges(Reg[] regs, int idx, BitSet edges) {
        regs[idx].excludes.or(edges);
        for (int i = edges.nextSetBit(0); i >= 0; i = edges.nextSetBit(i + 1)) {
            regs[i].excludes.set(idx);
        }
    }

    /** Aggressive mode: anything defined in a try range interferes with the locals live at its handlers */
    private static void addTrapEdges(IrMethod method, Reg[] regs, Liveness live) {
        BitSet handlerLive = new BitSet(regs.length);
        BitSet edges = new BitSet(regs.length);
        for (Trap trap : method.traps) {
            handlerLive.clear();
            for (LabelStmt handler : trap.handlers) {
                BitSet in = live.liveIn(handler);
                if (in != null) {
                    handlerLive.or(in);
                }
            }
            if (handlerLive.isEmpty()) {
                continue;
            }
            for (Stmt s = trap.start; s != null && s != trap.end; s = s.getNext()) {
                if ((s.st == ST.ASSIGN || s.st == ST.IDENTITY) && s.getOp1().vt == VT.LOCAL) {
                    int idx = ((Local) s.getOp1()).lsIndex;
                    edges.clear();
                    edges.or(handlerLive);
                    edges.clear(idx);
                    Value op2 = s.getOp2();
                    if (op2.vt == VT.LOCAL) {
                        edges.clear(((Local) op2).lsIndex); // same value, same type
                    }
                    addEdges(regs, idx, edges);
                }
            }
        }
    }

    /**
     * Aggressive mode: merges the operands of every copy that don't interfere (union of their
     * interference), so they get one slot. Leaves one representative per merged group in [groups].
     */
    private static void coalesce(IrMethod method, Reg[] regs, Map<Character, List<Reg>> groups) {
        int[] parent = new int[regs.length];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        boolean merged = false;
        for (Stmt stmt : method.stmts) {
            if (stmt.st != ST.ASSIGN || stmt.getOp1().vt != VT.LOCAL || stmt.getOp2().vt != VT.LOCAL) {
                continue;
            }
            int a = find(parent, ((Local) stmt.getOp1()).lsIndex);
            int b = find(parent, ((Local) stmt.getOp2()).lsIndex);
            if (a == b) {
                continue;
            }
            Reg ra = regs[a];
            Reg rb = regs[b];
            if (ra.type != rb.type || (ra.reg >= 0 && rb.reg >= 0)) {
                continue;
            }
            if (interferes(ra, rb)) {
                continue;
            }
            if (rb.reg >= 0) { // a parameter slot is kept by the merged group
                Reg t = ra;
                ra = rb;
                rb = t;
            }
            parent[rb.index] = ra.index;
            ra.excludes.or(rb.excludes);
            if (ra.members == null) {
                ra.members = new BitSet(regs.length);
                ra.members.set(ra.index);
            }
            if (rb.members == null) {
                ra.members.set(rb.index);
            } else {
                ra.members.or(rb.members);
                rb.members = null;
            }
            merged = true;
        }
        if (!merged) {
            return;
        }
        for (List<Reg> list : groups.values()) {
            list.removeIf(r -> parent[r.index] != r.index);
            for (Reg r : list) {
                if (r.members != null) {
                    r.excludes.andNot(r.members);
                    if (r.reg >= 0) { // fixed slot: known before coloring the rest
                        for (int m = r.members.nextSetBit(0); m >= 0; m = r.members.nextSetBit(m + 1)) {
                            regs[m].reg = r.reg;
                        }
                    }
                }
                r.excludeCount = r.excludes.cardinality();
                r.prefers.clear(); // whatever is still copied to/from this group interferes with it
            }
        }
    }

    private static boolean interferes(Reg a, Reg b) {
        if (b.members == null) {
            return a.excludes.get(b.index);
        }
        if (a.members == null) {
            return b.excludes.get(a.index);
        }
        return a.excludes.intersects(b.members);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private Map<Character, List<Reg>> groupAndCleanUpByType(Reg[] regs, int localCount) {
        Map<Character, List<Reg>> groups = new HashMap<>();
        Map<Character, BitSet> masks = new HashMap<>();
        for (Reg reg : regs) {
            groups.computeIfAbsent(reg.type, k -> new ArrayList<>()).add(reg);
            masks.computeIfAbsent(reg.type, k -> new BitSet(localCount)).set(reg.index);
        }
        for (Reg reg : regs) {
            reg.excludes.and(masks.get(reg.type));
            reg.excludeCount = reg.excludes.cardinality();
            reg.prefers.removeIf(ex -> ex.type != reg.type);
        }
        return groups;
    }
}
