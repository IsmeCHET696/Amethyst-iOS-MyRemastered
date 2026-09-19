package net.kdt.pojavlaunch.utils;

import java.util.*;
import java.io.*;

import net.kdt.pojavlaunch.*;

public class MCOptionUtils
{
    private static List<String> mLineList;
    
    public static void load() {
        if (mLineList == null) {
            mLineList = new ArrayList<String>();
        } else {
            mLineList.clear();
        }
        
        try {
            BufferedReader reader = new BufferedReader(new FileReader(Tools.DIR_GAME_PROFILE + "/options.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                mLineList.add(line);
            }
            reader.close();
        } catch (IOException e) {
            System.err.println("Could not load options.txt");
            e.printStackTrace();
        }
    }
    
    public static void set(String key, String value) {
        // Task 104（26.3 30fps 根治）：去重写入。MC 的 Options.load 逐行
        // putString —— 同 key 多行时【后出现的行覆盖先出现的行】。旧实现只改
        // 第一处、残留后续旧行，MC 侧会用残留行覆盖我们写入的值（实测现场：
        // 启动器明明写了 inactivityFpsLimit:minimized，26.3 仍以 AFK 模式跑
        // → SHORT_AFK → min(maxFps, 30) = 30fps）。现在第一处替换、
        // 后续重复行全部删除，保证 MC 侧读到的就是我们写的值。
        String prefix = key + ":";
        boolean replaced = false;
        ListIterator<String> it = mLineList.listIterator();
        while (it.hasNext()) {
            String line = it.next();
            if (line.startsWith(prefix)) {
                if (!replaced) {
                    it.set(key + ":" + value);
                    replaced = true;
                } else {
                    it.remove(); // 重复行：MC 侧后行覆盖前行，必须清除
                }
            }
        }
        if (!replaced) {
            mLineList.add(key + ":" + value);
        }
    }

    public static void setDefault(String key, String value) {
        if (get(key) == null) {
            mLineList.add(key + ":" + value);
        }
    }

    /**
     * 移除 options.txt 中指定 key 的行（如果存在）。
     * 用于在用户选择"默认"时清除之前写入的值，让 MC 使用内部默认行为。
     * 注意：调用前必须先 load()。
     */
    public static void remove(String key) {
        if (mLineList == null) return;
        String prefix = key + ":";
        for (int i = 0; i < mLineList.size(); i++) {
            if (mLineList.get(i).startsWith(prefix)) {
                mLineList.remove(i);
                return;
            }
        }
    }

    public static String get(String key){
        if (mLineList == null){
            load();
        }
        for (int i = 0; i < mLineList.size(); i++) {
            String line = mLineList.get(i);
            if (line.startsWith(key + ":")) {
                String value = mLineList.get(i);
                return value.substring(value.indexOf(":")+1);
            }
        }
        return null;
    }

    public static void save() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < mLineList.size(); i++) {
            result.append(mLineList.get(i));
            if (i + 1 < mLineList.size()) {
                result.append("\n");
            }
        }
        
        try {
            Tools.write(Tools.DIR_GAME_PROFILE + "/options.txt", result.toString());
        } catch (IOException e) {
            System.err.println("Could not save options.txt");
            e.printStackTrace();
        }
        mLineList = null;
    }

    /**
     * Task 104（26.3 30fps 取证闭环）：从磁盘【重新读取】指定 key 的值
     * （绕过内存链表）。save() 后内存 get() 只能证明内存态，不能证明 MC
     * 即将读取的 options.txt 内容——写入路径错位 / 写入失败 / 重复行残留
     * 都会让内存态与磁盘态分叉；26.3 的 30fps 限帧正是这种分叉的实测现场。
     * 出现多次时返回最后一次的值（与 MC 侧 last-wins 语义一致）并告警。
     */
    public static String getFromFile(String key) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(Tools.DIR_GAME_PROFILE + "/options.txt"));
            String line;
            String last = null;
            int occurrences = 0;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key + ":")) {
                    last = line.substring(line.indexOf(":") + 1);
                    occurrences++;
                }
            }
            reader.close();
            if (occurrences > 1) {
                System.out.println("[MCOptionUtils] Task104 WARNING: '" + key + "' appears " + occurrences
                    + " times in options.txt (last-wins at MC side): " + last);
            }
            return last;
        } catch (IOException e) {
            return "<read-failed: " + e.getClass().getSimpleName() + ">";
        }
    }
}
