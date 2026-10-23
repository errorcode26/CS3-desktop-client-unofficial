package com.lagradost.runtime.dex;

import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.ir.expr.Local;
import com.googlecode.dex2jar.ir.expr.Value;
import com.googlecode.dex2jar.ir.expr.Value.VT;
import com.googlecode.dex2jar.ir.stmt.AssignStmt;
import com.googlecode.dex2jar.ir.stmt.BaseSwitchStmt;
import com.googlecode.dex2jar.ir.stmt.JumpStmt;
import com.googlecode.dex2jar.ir.stmt.LabelStmt;
import com.googlecode.dex2jar.ir.stmt.Stmt;
import com.googlecode.dex2jar.ir.stmt.Stmt.ST;
import com.googlecode.dex2jar.ir.ts.Cfg;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Backward liveness over the statement level control flow graph using bit sets.
 * <p>
 * dex2jar computes the same information with one object per (statement, local) pair
 * (BaseAnalyze frames), which needs gigabytes for the huge Kotlin coroutine state machines found in
 * some extensions. This computes the exact same "used" relation with one bit per (statement, local):
 * <ul>
 * <li>a local is only tracked at a statement if dex2jar's frame has an entry for it, i.e. it was
 * assigned on the path along which Cfg.dfs first reached the statement ("present")</li>
 * <li>it is live-in if used there, or live-in at a successor (not across its own assignment,
 * exception handlers see the state before the statement)</li>
 * </ul>
 * Statements must carry a {@link Node} in {@link Stmt#frame} when reachable (see {@link #buildTree}).
 */
final class LowMemLiveness {

    /** Per statement data attached to {@link Stmt#frame} while a transformer runs. */
    static final class Node {
        final Stmt stmt;
        int idx = -1;
        /** tree parent in the order dex2jar's Cfg.dfs first reached the statement */
        Node parent;
        /** true when the frame came from the parent's state before execution (exception edge) */
        boolean excEdge;
        int depth;
        int pre;
        int post;
        List<Node> children;

        Node(Stmt stmt) {
            this.stmt = stmt;
        }
    }

    /** A frame as passed around by Cfg.dfs: the statement's node, before or after its execution */
    static final class Frame {
        final Node node;
        final boolean after;

        Frame(Node node, boolean after) {
            this.node = node;
            this.after = after;
        }
    }

    /** A merge of a frame into a label with phis (recorded for UnSSA) */
    static final class Merge {
        final LabelStmt label;
        final Stmt from;
        final Node fromNode;
        final boolean after;

        Merge(LabelStmt label, Stmt from, Node fromNode, boolean after) {
            this.label = label;
            this.from = from;
            this.fromNode = fromNode;
            this.after = after;
        }
    }

    /**
     * Replays Cfg.dfs (same traversal order as dex2jar's analyses) and attaches a {@link Node} to
     * every reachable statement's frame. Also sets {@link Stmt#visited} like Cfg.dfs.
     *
     * @param merges if not null, receives every merge into a label that has phis, in order
     * @return the nodes in creation order (parents before children)
     */
    static List<Node> buildTree(IrMethod method, final List<Merge> merges) {
        final List<Node> created = new ArrayList<>();
        Cfg.dfs(method.stmts, new Cfg.FrameVisitor<Frame>() {
            @Override
            public Frame merge(Frame srcFrame, Frame distFrame, Stmt src, Stmt dist) {
                if (distFrame == null) {
                    Node n = new Node(dist);
                    n.parent = srcFrame.node;
                    n.excEdge = !srcFrame.after;
                    n.depth = srcFrame.node.depth + 1;
                    created.add(n);
                    distFrame = new Frame(n, false);
                }
                if (merges != null && dist.st == ST.LABEL) {
                    LabelStmt label = (LabelStmt) dist;
                    if (label.phis != null && !label.phis.isEmpty()) {
                        merges.add(new Merge(label, src, srcFrame.node, srcFrame.after));
                    }
                }
                return distFrame;
            }

            @Override
            public Frame initFirstFrame(Stmt first) {
                Node n = new Node(first);
                created.add(n);
                return new Frame(n, false);
            }

            @Override
            public Frame exec(Frame frame, Stmt stmt) {
                return new Frame(frame.node, true);
            }
        });
        for (Stmt s = method.stmts.getFirst(); s != null; s = s.getNext()) {
            if (s.frame instanceof Frame) {
                s.frame = ((Frame) s.frame).node;
            }
        }
        return created;
    }

    final Stmt[] stmts;
    final int localCount;
    final int[] def;
    final int[][] uses;
    final BitSet[] liveIn;
    /** locals that have an entry in dex2jar's frame at each statement */
    final BitSet[] present;
    /** phi locals defined at each (label) statement, or null */
    final BitSet[] phiMask;
    /** extra locals live at the end of a normal edge from a statement (phi operands) */
    final BitSet[] extraNormalOut;
    /** extra locals live before an exceptional edge from a statement (phi operands) */
    final BitSet[] extraExcOut;
    final int[][] normalSuccs;
    final int[][] excSuccs;

    private LowMemLiveness(Stmt[] stmts, int localCount) {
        this.stmts = stmts;
        this.localCount = localCount;
        int n = stmts.length;
        def = new int[n];
        uses = new int[n][];
        liveIn = new BitSet[n];
        present = new BitSet[n];
        phiMask = new BitSet[n];
        extraNormalOut = new BitSet[n];
        extraExcOut = new BitSet[n];
        normalSuccs = new int[n][];
        excSuccs = new int[n][];
    }

    static Node node(Stmt s) {
        return s == null ? null : (Node) s.frame;
    }

    /**
     * Assign dense indexes to reachable statements (those with a {@link Node} in frame), in list
     * order, and compute uses, definitions and presence.
     *
     * @param created the nodes from {@link #buildTree}, in creation order
     */
    static LowMemLiveness index(IrMethod method, int localCount, List<Node> created) {
        List<Stmt> list = new ArrayList<>();
        for (Stmt s = method.stmts.getFirst(); s != null; s = s.getNext()) {
            Node n = node(s);
            if (n != null) {
                n.idx = list.size();
                list.add(s);
            }
        }
        LowMemLiveness l = new LowMemLiveness(list.toArray(new Stmt[0]), localCount);
        IntList tmp = new IntList();
        for (int i = 0; i < l.stmts.length; i++) {
            Stmt s = l.stmts[i];
            tmp.clear();
            final IntList u = tmp;
            l.def[i] = -1;
            Cfg.travel(s, new Cfg.TravelCallBack() {
                @Override
                public Value onAssign(Local v, AssignStmt as) {
                    return v;
                }

                @Override
                public Value onUse(Local v) {
                    u.add(v.lsIndex);
                    return v;
                }
            }, false);
            if ((s.st == ST.ASSIGN || s.st == ST.IDENTITY) && s.getOp1().vt == VT.LOCAL) {
                l.def[i] = ((Local) s.getOp1()).lsIndex;
            }
            l.uses[i] = tmp.toArray();
            if (s.st == ST.LABEL) {
                LabelStmt label = (LabelStmt) s;
                if (label.phis != null && !label.phis.isEmpty()) {
                    BitSet m = new BitSet(localCount);
                    for (AssignStmt phi : label.phis) {
                        m.set(((Local) phi.getOp1()).lsIndex);
                    }
                    l.phiMask[i] = m;
                }
            }
        }
        // pre-index static control flow graph successors and exception handlers
        List<Stmt> succList = new ArrayList<>(8);
        IntList succIndices = new IntList();
        for (int i = 0; i < l.stmts.length; i++) {
            Stmt s = l.stmts[i];
            normalSuccessors(s, succList);
            succIndices.clear();
            for (Stmt t : succList) {
                Node tn = node(t);
                if (tn != null && tn.idx >= 0) {
                    succIndices.add(tn.idx);
                }
            }
            l.normalSuccs[i] = succIndices.toArray();

            succIndices.clear();
            if (s.exceptionHandlers != null) {
                for (LabelStmt h : s.exceptionHandlers) {
                    Node hn = node(h);
                    if (hn != null && hn.idx >= 0) {
                        succIndices.add(hn.idx);
                    }
                }
            }
            l.excSuccs[i] = succIndices.toArray();
        }
        // presence: entries are copied from the first merging frame (after execution for normal
        // edges, before for exception edges); phi entries are created at their label
        for (Node n : created) {
            if (n.idx < 0) continue;
            BitSet p;
            Node parent = n.parent;
            if (parent == null || parent.idx < 0) {
                p = new BitSet(localCount);
            } else {
                BitSet pp = l.present[parent.idx];
                int d = l.def[parent.idx];
                boolean addsDef = !n.excEdge && d >= 0 && !pp.get(d);
                if (!addsDef && l.phiMask[n.idx] == null) {
                    p = pp; // unchanged, share the (never mutated) set
                } else {
                    p = (BitSet) pp.clone();
                    if (addsDef) p.set(d);
                }
            }
            if (l.phiMask[n.idx] != null) {
                if (p == (parent == null || parent.idx < 0 ? null : l.present[parent.idx])) p = (BitSet) p.clone();
                p.or(l.phiMask[n.idx]);
            }
            l.present[n.idx] = p;
        }
        return l;
    }

    void addNormalOut(int stmtIdx, int local) {
        BitSet b = extraNormalOut[stmtIdx];
        if (b == null) {
            b = extraNormalOut[stmtIdx] = new BitSet(localCount);
        }
        b.set(local);
    }

    void addExcOut(int stmtIdx, int local) {
        BitSet b = extraExcOut[stmtIdx];
        if (b == null) {
            b = extraExcOut[stmtIdx] = new BitSet(localCount);
        }
        b.set(local);
    }

    /** Normal successors, same order and rules as Cfg.dfs */
    static void normalSuccessors(Stmt s, List<Stmt> out) {
        out.clear();
        if (s.st.canSwitch()) {
            BaseSwitchStmt bs = (BaseSwitchStmt) s;
            for (LabelStmt t : bs.targets) {
                out.add(t);
            }
            out.add(bs.defaultTarget);
        }
        if (s.st.canBranch()) {
            out.add(((JumpStmt) s).getTarget());
        }
        if (s.st.canContinue()) {
            Stmt next = s.getNext();
            if (next != null) {
                out.add(next);
            }
        }
    }

    void solve() {
        int n = stmts.length;
        BitSet cur = new BitSet(localCount);
        BitSet scratch = new BitSet(localCount);
        for (int i = 0; i < n; i++) {
            liveIn[i] = new BitSet(localCount);
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = n - 1; i >= 0; i--) {
                cur.clear();
                for (int targetIdx : normalSuccs[i]) {
                    BitSet mask = phiMask[targetIdx];
                    if (mask == null) {
                        cur.or(liveIn[targetIdx]);
                    } else {
                        scratch.clear();
                        scratch.or(liveIn[targetIdx]);
                        scratch.andNot(mask);
                        cur.or(scratch);
                    }
                }
                if (extraNormalOut[i] != null) {
                    cur.or(extraNormalOut[i]);
                }
                if (def[i] >= 0) {
                    cur.clear(def[i]);
                }
                for (int handlerIdx : excSuccs[i]) {
                    BitSet mask = phiMask[handlerIdx];
                    if (mask == null) {
                        cur.or(liveIn[handlerIdx]);
                    } else {
                        scratch.clear();
                        scratch.or(liveIn[handlerIdx]);
                        scratch.andNot(mask);
                        cur.or(scratch);
                    }
                }
                if (extraExcOut[i] != null) {
                    cur.or(extraExcOut[i]);
                }
                for (int u : uses[i]) {
                    cur.set(u);
                }
                // only locals with a frame entry are tracked (and can become used)
                cur.and(present[i]);
                if (!cur.equals(liveIn[i])) {
                    liveIn[i].clear();
                    liveIn[i].or(cur);
                    changed = true;
                }
            }
        }
    }

    BitSet liveIn(Stmt s) {
        Node n = node(s);
        return n == null || n.idx < 0 ? null : liveIn[n.idx];
    }

    static final class IntList {
        private static final int[] EMPTY_INT_ARRAY = new int[0];
        int[] data = new int[8];
        int size;

        void add(int v) {
            if (size == data.length) {
                data = java.util.Arrays.copyOf(data, size * 2);
            }
            data[size++] = v;
        }

        void clear() {
            size = 0;
        }

        int[] toArray() {
            return size == 0 ? EMPTY_INT_ARRAY : java.util.Arrays.copyOf(data, size);
        }
    }
}
