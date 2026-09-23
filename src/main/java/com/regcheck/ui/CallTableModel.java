package com.regcheck.ui;

import com.regcheck.core.Call;
import com.regcheck.core.History;
import com.regcheck.core.Operation;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * 可编辑的调用记录表模型。单元格编辑后生成新的不可变 {@link Call}。
 * 返回序号与结果留空表示该调用没有返回。
 */
public final class CallTableModel extends AbstractTableModel {

    private static final String[] COLUMNS =
            {"编号", "客户端", "操作", "调用序号", "返回序号", "结果"};

    private final List<Call> calls = new ArrayList<>();

    public void setCalls(List<Call> newCalls) {
        calls.clear();
        calls.addAll(newCalls);
        fireTableDataChanged();
    }

    public List<Call> getCalls() {
        return List.copyOf(calls);
    }

    public History toHistory() {
        return new History(getCalls());
    }

    public void addRow() {
        int nextId = calls.stream().mapToInt(Call::id).max().orElse(0) + 1;
        calls.add(new Call(nextId, "C" + nextId, Operation.read(),
                nextId * 2L - 1, nextId * 2L, 0L));
        fireTableRowsInserted(calls.size() - 1, calls.size() - 1);
    }

    public void removeRow(int index) {
        if (index >= 0 && index < calls.size()) {
            calls.remove(index);
            fireTableRowsDeleted(index, index);
        }
    }

    @Override
    public int getRowCount() {
        return calls.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int col) {
        Call c = calls.get(row);
        return switch (col) {
            case 0 -> c.id();
            case 1 -> c.client();
            case 2 -> c.op().toString();
            case 3 -> c.invokeSeq();
            case 4 -> c.responseSeq() == null ? "" : c.responseSeq();
            case 5 -> c.result() == null ? "" : c.result();
            default -> throw new IllegalArgumentException("列越界 " + col);
        };
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return true;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        Call old = calls.get(row);
        String s = value == null ? "" : value.toString().trim();
        try {
            Call updated = switch (col) {
                case 0 -> new Call(Integer.parseInt(s), old.client(), old.op(),
                        old.invokeSeq(), old.responseSeq(), old.result());
                case 1 -> new Call(old.id(), s, old.op(),
                        old.invokeSeq(), old.responseSeq(), old.result());
                case 2 -> new Call(old.id(), old.client(), Operation.parse(s),
                        old.invokeSeq(), old.responseSeq(), old.result());
                case 3 -> new Call(old.id(), old.client(), old.op(),
                        Long.parseLong(s), old.responseSeq(), old.result());
                case 4 -> new Call(old.id(), old.client(), old.op(), old.invokeSeq(),
                        s.isEmpty() ? null : Long.parseLong(s), old.result());
                case 5 -> new Call(old.id(), old.client(), old.op(), old.invokeSeq(),
                        old.responseSeq(), s.isEmpty() ? null : parseResult(s));
                default -> throw new IllegalArgumentException("列越界 " + col);
            };
            calls.set(row, updated);
            fireTableRowsUpdated(row, row);
        } catch (RuntimeException parseError) {
            // 非法输入不写入模型；恢复显示旧值，由校验器在分析时报告问题。
            java.awt.Toolkit.getDefaultToolkit().beep();
            fireTableCellUpdated(row, col);
        }
    }

    private static Object parseResult(String s) {
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) {
            return Boolean.valueOf(s);
        }
        return Long.parseLong(s);
    }
}
