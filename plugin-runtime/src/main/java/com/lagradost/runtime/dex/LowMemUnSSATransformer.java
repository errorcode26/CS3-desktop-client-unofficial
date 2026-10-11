package com.lagradost.runtime.dex;

import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.ir.expr.Local;
import com.googlecode.dex2jar.ir.expr.PhiExpr;
import com.googlecode.dex2jar.ir.expr.Value;
import com.googlecode.dex2jar.ir.expr.Value.VT;
import com.googlecode.dex2jar.ir.stmt.AssignStmt;
import com.googlecode.dex2jar.ir.stmt.JumpStmt;
import com.googlecode.dex2jar.ir.stmt.LabelStmt;
import com.googlecode.dex2jar.ir.stmt.Stmt;
import com.googlecode.dex2jar.ir.stmt.Stmt.ST;
import com.googlecode.dex2jar.ir.stmt.StmtList;
import com.googlecode.dex2jar.ir.stmt.Stmts;
import com.googlecode.dex2jar.ir.ts.Cfg;
import com.googlecode.dex2jar.ir.ts.Transformer;
import com.lagradost.runtime.dex.Liveness.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drop-in replacement for dex2jar's UnSSATransformer (Apache 2.0, Panxiaobo) with the same results
 * but linear memory.
 * <p>
 * The original tracks one value object per (statement, local) pair to (a) find which phi operand
 * flows from each predecessor and (b) build an interference graph. Here (a) is answered with the
 * spanning tree created by Cfg.dfs (the "first merge" tree the original frames follow), with the
 * same closest-definition rule, and (b) with bit set liveness.
 */
public class LowMemUnSSATransformer implements Transformer {

    private Node[] byPre;
    private int[][] up;
    private int maxLog;
    /** local index -> defining statements (assignments) */
    private List<Stmt>[] defs;
    /** local index -> label of the phi defining it */
    private LabelStmt[] phiDef;

    @Override
    public void transform(IrMethod method) {
        if (method.phiLabels == null || method.phiLabels.isEmpty()) {
            return;
        }

        // github issue 186 workaround, same as the original
        for (LabelStmt phiLabel : method.phiLabels) {
            Stmt stmt = phiLabel.getNext();
            if (stmt.st == ST.LABEL) {
                LabelStmt labelStmt2 = (LabelStmt) stmt;
                if (labelStmt2.phis != null && !labelStmt2.phis.isEmpty()) {
                    method.stmts.insertAfter(phiLabel, Stmts.nNop());
                }
            }
        }

        int localCount = Cfg.reIndexLocal(method);
        Cfg.createCFG(method);

        // 1. replay Cfg.dfs to get the first-merge tree and every merge into a phi label
        final List<Liveness.Merge> merges = new ArrayList<>();
        List<Node> created = Liveness.buildTree(method, merges);
        buildTree(method);
        collectDefs(method, localCount);

        // 2. which operand flows from each predecessor (stmt2regMap in the original); later merges win
        Map<AssignStmt, Map<Stmt, Local>> chosen = new IdentityHashMap<>();
        Liveness live = Liveness.index(method, localCount, created);
        for (Liveness.Merge m : merges) {
            for (AssignStmt phi : m.label.phis) {
                Local a = (Local) phi.getOp1();
                Local best = null;
                int bestHops = Integer.MAX_VALUE;
                int h = hops(m.fromNode, m.after, a);
                if (h >= 0) {
                    best = a;
                    bestHops = h;
                }
                for (Value op : ((PhiExpr) phi.getOp2()).getOps()) {
                    Local l = (Local) op;
                    int hl = hops(m.fromNode, m.after, l);
                    if (hl >= 0 && hl < bestHops) { // strict: stable sort keeps the earlier one on ties
                        best = l;
                        bestHops = hl;
                    }
                }
                if (best == null) {
                    continue;
                }
                chosen.computeIfAbsent(phi, k -> new IdentityHashMap<>()).put(m.from, best);
                if (m.after) {
                    live.addNormalOut(m.fromNode.idx, best.lsIndex);
                } else {
                    live.addExcOut(m.fromNode.idx, best.lsIndex);
                }
            }
        }
        live.solve();

        // 3. introduce a new local where the phi result interferes with an operand (fixPhi)
        for (LabelStmt labelStmt : method.phiLabels) {
            for (AssignStmt phi : labelStmt.phis) {
                Local a = (Local) phi.getOp1();
                boolean introduceNewLocal = false;
                for (Value op : ((PhiExpr) phi.getOp2()).getOps()) {
                    if (interferes(live, a, (Local) op)) {
                        introduceNewLocal = true;
                        break;
                    }
                }
                if (introduceNewLocal) {
                    Local newLocal = (Local) a.clone();
                    phi.op1 = newLocal;
                    method.locals.add(newLocal);
                    Stmt newAssignStmt = Stmts.nAssign(a, newLocal);
                    Stmt next = labelStmt.getNext();
                    if (next != null && next.st == ST.IDENTITY && next.getOp2().vt == VT.EXCEPTION_REF) {
                        method.stmts.insertAfter(next, newAssignStmt);
                    } else {
                        method.stmts.insertAfter(labelStmt, newAssignStmt);
                    }
                }
            }
        }

        // 4. insert the copies on every path into the phi labels (insertAssignPath)
        List<AssignStmt> buff = new ArrayList<>();
        for (LabelStmt labelStmt : method.phiLabels) {
            List<AssignStmt> phis = labelStmt.phis;
            for (Stmt from : labelStmt.cfgFroms) {
                if (from.visited) {
                    for (AssignStmt phi : phis) {
                        Local a = (Local) phi.getOp1();
                        Map<Stmt, Local> m = chosen.get(phi);
                        Local local = m == null ? null : m.get(from);
                        if (local != null && local != a) {
                            buff.add(Stmts.nAssign(a, local));
                        }
                    }
                    insertAssignPath(method.stmts, from, labelStmt, buff);
                    buff.clear();
                }
            }
        }

        // 5. clean up
        for (Local local : method.locals) {
            local.tag = null;
        }
        for (Stmt stmt : method.stmts) {
            stmt.frame = null;
        }
        for (LabelStmt labelStmt : method.phiLabels) {
            labelStmt.phis = null;
        }
        method.phiLabels = null;
        byPre = null;
        up = null;
        defs = null;
        phiDef = null;
    }

    private static void insertAssignPath(StmtList stmts, Stmt from, LabelStmt labelStmt, List<AssignStmt> buff) {
        boolean insertBeforeFromStmt;
        if (from.exceptionHandlers != null && from.exceptionHandlers.contains(labelStmt)) {
            insertBeforeFromStmt = true;
        } else {
            switch (from.st) {
            case GOTO:
            case IF:
                JumpStmt jumpStmt = (JumpStmt) from;
                insertBeforeFromStmt = jumpStmt.getTarget().equals(labelStmt);
                break;
            case TABLE_SWITCH:
            case LOOKUP_SWITCH:
                insertBeforeFromStmt = true;
                break;
            default:
                insertBeforeFromStmt = false;
                break;
            }
        }
        if (insertBeforeFromStmt) {
            for (AssignStmt as : buff) {
                stmts.insertBefore(from, as);
            }
        } else {
            for (AssignStmt as : buff) {
                stmts.insertAfter(from, as);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private void buildTree(IrMethod method) {
        List<Node> nodes = new ArrayList<>();
        Node root = null;
        for (Stmt s = method.stmts.getFirst(); s != null; s = s.getNext()) {
            Node n = Liveness.node(s);
            if (n == null) {
                continue;
            }
            nodes.add(n);
            if (n.parent == null) {
                root = n;
            } else {
                if (n.parent.children == null) {
                    n.parent.children = new ArrayList<>(2);
                }
                n.parent.children.add(n);
            }
        }
        byPre = new Node[nodes.size()];
        // iterative pre/post order numbering
        int counter = 0;
        ArrayDeque<Object[]> stack = new ArrayDeque<>();
        if (root != null) {
            root.pre = counter;
            byPre[counter++] = root;
            stack.push(new Object[]{root, 0});
        }
        while (!stack.isEmpty()) {
            Object[] top = stack.peek();
            Node n = (Node) top[0];
            int ci = (Integer) top[1];
            if (n.children != null && ci < n.children.size()) {
                top[1] = ci + 1;
                Node c = n.children.get(ci);
                c.pre = counter;
                byPre[counter++] = c;
                stack.push(new Object[]{c, 0});
            } else {
                n.post = counter - 1; // last pre index in the subtree
                stack.pop();
            }
        }
        int size = counter;
        maxLog = 1;
        while ((1 << maxLog) < Math.max(2, size)) {
            maxLog++;
        }
        up = new int[maxLog + 1][size];
        for (int i = 0; i < size; i++) {
            Node n = byPre[i];
            up[0][i] = n.parent == null ? i : n.parent.pre;
        }
        for (int k = 1; k <= maxLog; k++) {
            int[] prev = up[k - 1];
            int[] cur = up[k];
            for (int i = 0; i < size; i++) {
                cur[i] = prev[prev[i]];
            }
        }
    }

    private static boolean isAncestorOrSelf(Node a, Node b) {
        return a.pre <= b.pre && b.pre <= a.post;
    }

    private Node ancestorAtDepth(Node n, int depth) {
        int diff = n.depth - depth;
        int cur = n.pre;
        for (int k = 0; diff > 0; k++, diff >>= 1) {
            if ((diff & 1) != 0) {
                cur = up[k][cur];
            }
        }
        return byPre[cur];
    }

    @SuppressWarnings("unchecked")
    private void collectDefs(IrMethod method, int localCount) {
        defs = new List[localCount];
        phiDef = new LabelStmt[localCount];
        for (Stmt s = method.stmts.getFirst(); s != null; s = s.getNext()) {
            if ((s.st == ST.ASSIGN || s.st == ST.IDENTITY) && s.getOp1().vt == VT.LOCAL) {
                int idx = ((Local) s.getOp1()).lsIndex;
                if (defs[idx] == null) {
                    defs[idx] = new ArrayList<>(1);
                }
                defs[idx].add(s);
            }
        }
        for (LabelStmt label : method.phiLabels) {
            for (AssignStmt phi : label.phis) {
                phiDef[((Local) phi.getOp1()).lsIndex] = label;
            }
        }
    }

    /**
     * Distance (in tree edges) from the definition of {@code x} to the frame of {@code from}
     * (before execution when {@code after} is false), or -1 if {@code x} is not in that frame.
     */
    private int hops(Node from, boolean after, Local x) {
        int idx = x.lsIndex;
        if (idx < 0 || idx >= phiDef.length) {
            return -1;
        }
        LabelStmt pl = phiDef[idx];
        List<Stmt> ds = defs[idx];
        if (pl != null && (ds == null || ds.isEmpty())) {
            Node ln = Liveness.node(pl);
            if (ln == null || !isAncestorOrSelf(ln, from)) {
                return -1;
            }
            return from.depth - ln.depth;
        }
        if (ds == null) {
            return -1;
        }
        if (ds.size() == 1 && pl == null) {
            return defHops(Liveness.node(ds.get(0)), from, after);
        }
        // not in SSA form for this local: the closest definition along the tree path wins
        int best = -1;
        if (pl != null) {
            Node ln = Liveness.node(pl);
            if (ln != null && isAncestorOrSelf(ln, from)) {
                best = from.depth - ln.depth;
            }
        }
        for (Stmt d : ds) {
            int h = defHops(Liveness.node(d), from, after);
            if (h >= 0 && (best < 0 || h < best)) {
                best = h;
            }
        }
        return best;
    }

    private int defHops(Node dn, Node from, boolean after) {
        if (dn == null) {
            return -1;
        }
        if (dn == from) {
            return after ? 0 : -1;
        }
        if (!isAncestorOrSelf(dn, from)) {
            return -1;
        }
        Node child = ancestorAtDepth(from, dn.depth + 1);
        if (child.excEdge) {
            return -1; // the definition is not visible to exception handlers of its own statement
        }
        return from.depth - dn.depth;
    }

    /** Whether the phi result {@code a} and operand {@code b} would share an edge in the original register graph */
    private boolean interferes(Liveness live, Local a, Local b) {
        if (a == b) {
            return false;
        }
        LabelStmt la = phiDef[a.lsIndex];
        if (la != null) {
            BitSet in = live.liveIn(la);
            if (in != null && in.get(b.lsIndex)) {
                return true;
            }
        }
        LabelStmt lb = phiDef[b.lsIndex];
        if (lb != null) {
            BitSet in = live.liveIn(lb);
            if (in != null && in.get(a.lsIndex)) {
                return true;
            }
        }
        return defEdge(live, b, a) || defEdge(live, a, b);
    }

    /** An edge created at a definition of {@code x}: {@code y} live at a successor of that definition */
    private boolean defEdge(Liveness live, Local x, Local y) {
        List<Stmt> ds = defs[x.lsIndex];
        if (ds == null) {
            return false;
        }
        Set<Stmt> tos = new HashSet<>();
        for (Stmt s : ds) {
            tos.clear();
            Cfg.collectTos(s, tos);
            for (Stmt t : tos) {
                BitSet in = live.liveIn(t);
                if (in == null) {
                    continue;
                }
                if (t.st == ST.LABEL) {
                    LabelStmt label = (LabelStmt) t;
                    if (label.phis != null) {
                        boolean excluded = false;
                        for (AssignStmt phi : label.phis) {
                            if (((Local) phi.getOp1()).lsIndex == y.lsIndex) {
                                excluded = true;
                                break;
                            }
                        }
                        if (excluded) {
                            continue;
                        }
                    }
                }
                if (in.get(y.lsIndex)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static final Map<String, String> UNUSED = new HashMap<>();
}
