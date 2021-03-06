package com.kunfei.bookshelf.help;

import android.text.TextUtils;
import android.util.Log;

import com.kunfei.bookshelf.bean.ReplaceRuleBean;
import com.kunfei.bookshelf.model.ReplaceRuleManager;
import com.luhuiguo.chinese.ChineseUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ChapterContentHelp {
    private static ChapterContentHelp instance;

    public static synchronized ChapterContentHelp getInstance() {
        if (instance == null)
            instance = new ChapterContentHelp();
        return instance;
    }

    /**
     * 转繁体
     */
    private String toTraditional(String content) {
        int convertCTS = ReadBookControl.getInstance().getTextConvert();
        switch (convertCTS) {
            case 0:
                break;
            case 1:
                content = ChineseUtils.toSimplified(content);
                break;
            case 2:
                content = ChineseUtils.toTraditional(content);
                break;
        }
        return content;
    }

    /**
     * 替换净化
     */
    public String replaceContent(String bookName, String bookTag, String content, Boolean replaceEnable) {
        if (!replaceEnable) return toTraditional(content);
        if (ReplaceRuleManager.getEnabled().size() == 0) return toTraditional(content);
        //替换
        for (ReplaceRuleBean replaceRule : ReplaceRuleManager.getEnabled()) {
            if (isUseTo(replaceRule.getUseTo(), bookTag, bookName)) {
                try {
                    content = content.replaceAll(replaceRule.getFixedRegex(), replaceRule.getReplacement());
                } catch (Exception ignored) {
                }
            }
        }
        return toTraditional(content);
    }

    /**
     * 段落重排算法入口。把整篇内容输入，连接错误的分段，再把每个段落调用其他方法重新切分
     *
     * @param content     正文
     * @param chapterName 标题
     * @return
     */
    public static String LightNovelParagraph2(String content, String chapterName) {
        if (ReadBookControl.getInstance().getLightNovelParagraph()) {
            String _content;
            int chapterNameLength = chapterName.trim().length();
            if (chapterNameLength > 1) {
                String regexp = chapterName.trim().replaceAll("\\s+", "(\\\\s*)");
//            质量较低的页面，章节内可能重复出现章节标题
                if (chapterNameLength > 5)
                    _content = content.replaceAll(regexp, "").trim();
                else
                    _content = content.replaceFirst("^\\s*" + regexp, "").trim();
            } else {
                _content = content;
            }

            String[] p = _content
                    .replaceAll("&quot;", "“")
                    .replaceAll("[:：]['\"‘”“]+","：“")
                    .replaceAll("[\"”“]+[\\s]*[\"”“][\\s\"”“]*", "”\n“")
                    .split("\n(\\s*)");

//      初始化StringBuffer的长度,在原content的长度基础上做冗余
            StringBuffer buffer = new StringBuffer((int) (content.length() * 1.15));
//          章节的文本格式为章节标题-空行-首段，所以处理段落时需要略过第一行文本。
            buffer.append(" ");

            if (!chapterName.trim().equals(p[0].trim())){
                // 去除段落内空格。unicode 3000 象形字间隔（中日韩符号和标点），不包含在\s内
                buffer.append(p[0].replaceAll("[\u3000\\s]+", ""));
            }

//      如果原文存在分段错误，需要把段落重新黏合
            for (int i = 1; i < p.length; i++) {
                if (match(MARK_SENTENCES_END, buffer.charAt(buffer.length() - 1)))
                    buffer.append("\n");
//            段落开头以外的地方不应该有空格
                // 去除段落内空格。unicode 3000 象形字间隔（中日韩符号和标点），不包含在\s内
                buffer.append(p[i].replaceAll("[\u3000\\s]", ""));

            }
            //     预分段预处理
            //         ”“处理为”\n“。
            //         ”。“处理为”。\n“。不考虑“？”  “！”的情况。
//                  ”。xxx处理为 ”。\n xxx
            p = buffer.toString()
                    .replaceAll("[\"”“]+[\\s]*[\"”“]+", "”\n“")
                    .replaceAll("[\"”“]+(？。！?!~)[\"”“]+", "”$1\n“")
                    .replaceAll("[\"”“]+(？。！?!~)([^\"”“])", "”$1\n$2")
                    .replaceAll("([问说喊唱叫骂道着答])[\\.。]", "$1。\n")
//                .replaceAll("([\\.。\\!！?？])([^\"”“]+)[:：][\"”“]", "$1\n$2：“")
                    .split("\n");

            buffer = new StringBuffer((int) (content.length() * 1.15));

            for (String s : p) {
                buffer.append("\n");
                buffer.append(FindNewLines(s)
                );
            }

            buffer = reduceLength(buffer);

            content = chapterName + "\n\n"
                    + buffer.toString()
//         处理章节头部空格和换行
                    .replaceFirst("^\\s+", "")
                    .replaceAll("\\s*[\"”“]+[\\s]*[\"”“][\\s\"”“]*", "”\n“")
                    .replaceAll("[:：][”“\"\\s]+","：“")
                    .replaceAll("\n[\"“”]([^\n\"“”]+)([,:，：][\"”“])([^\n\"“”]+)","\n$1：“$3")
                    .replaceAll("\n(\\s*)", "\n");
        }
        return content;
    }

    /**
     * 强制切分，减少段落内的句子
     * 如果连续2对引号的段落没有提示语，进入对话模式。最后一对引号后强制切分段落
     * 如果引号内的内容长于5句，可能引号状态有误，随机分段
     * 如果引号外的内容长于3句，随机分段
     *
     * @param str
     * @return
     */
    private static StringBuffer reduceLength(StringBuffer str) {
        String[] p = str.toString().split("\n");
        int l = p.length;
        boolean[] b = new boolean[l];

        for (int i = 0; i < l; i++) {
            if (p[i].matches(PARAGRAPH_DIAGLOG))
                b[i] = true;
            else
                b[i] = false;
        }

        int dialogue = 0;

        for (int i = 0; i < l; i++) {
            if (b[i]) {
                if (dialogue < 0)
                    dialogue = 1;
                else if (dialogue < 2)
                    dialogue++;
            } else {
                if (dialogue > 1) {
                    p[i] = splitQuote(p[i]);
                    dialogue--;
                } else if (dialogue > 0 && i < l - 2) {
                    if (b[i + 1])
                        p[i] = splitQuote(p[i]);
                }
            }
        }

        StringBuffer string = new StringBuffer();
        for (int i = 0; i < l; i++) {
            string.append('\n');
            string.append(p[i]);
//            System.out.print(" "+b[i]);
        }
//        System.out.println(" " + str);
        return string;
    }

    // 强制切分进入对话模式后，未构成 “xxx” 形式的段落
    private static String splitQuote(String str) {
//        System.out.println("splitQuote() " + str);
        int length = str.length();
        if (length < 3)
            return str;
        if (match(MARK_QUOTATION, str.charAt(0))) {
            int i = seekIndex(str, MARK_QUOTATION, 1, length - 2, true) + 1;
            if (i > 1)
                if(!match(MARK_QUOTATION_BEFORE,str.charAt(i-1)))
                    return str.substring(0, i) + "\n" + str.substring(i);
        } else if (match(MARK_QUOTATION, str.charAt(length - 1))) {
            int i = length - 1 - seekIndex(str, MARK_QUOTATION, 1, length - 2, false);
            if (i > 1)
                if(!match(MARK_QUOTATION_BEFORE,str.charAt(i-1)))
                    return str.substring(0, i) + "\n" + str.substring(i);
        }
        return str;
    }

    /**
     * 计算随机插入换行符的位置。
     * @param str 字符串
     * @param offset 传回的结果需要叠加的偏移量
     * @param min 最低几个句子，随机插入换行
     * @param gain 倍率。每个句子插入换行的数学期望 = 1 / gain , gain越大越不容易插入换行
     * @return
     */
    private static ArrayList<Integer> forceSplit(String str,int offset,int min,int gain,int tigger) {
        ArrayList<Integer> result=new ArrayList<>();
        ArrayList<Integer> array_end=seekIndexs(str,MARK_SENTENCES_END_P,0,str.length()-2,true);
        ArrayList<Integer> array_mid=seekIndexs(str,MARK_SENTENCES_MID,0,str.length()-2,true);
        if(array_end.size()<tigger && array_mid.size()<tigger*3)
            return result;
        int j=0;
        for(int i=min;i<array_end.size();i++){
            int k=0;
            for(;j<array_mid.size();j++){
                if(array_mid.get(j)<array_end.get(i))
                    k++;
            }
            if(Math.random()*gain<(0.8+k/2.5)){
                result.add(array_end.get(i)+offset);
                i=Math.max(i+min,i);
            }
        }
        return result;
    }

    // 对内容重新划分段落.输入参数str已经使用换行符预分割
    private static String FindNewLines(String str) {
        StringBuffer string = new StringBuffer(str);
        // 标记string中每个引号的位置.特别的，用引号进行列举时视为只有一对引号。 如：“锅”、“碗”视为“锅、碗”，从而避免误断句。
        List<Integer> array_quote = new ArrayList<>();
        //  标记插入换行符的位置，int为插入位置（str的char下标）
        ArrayList<Integer> ins_n = new ArrayList<>();

//      mod[i]标记str的每一段处于引号内还是引号外。范围： str.substring( array_quote.get(i), array_quote.get(i+1) )的状态。
//      长度：array_quote.size(),但是初始化时未预估占用的长度，用空间换时间
//      0未知，正数引号内，负数引号外。
//      如果相邻的两个标记都为+1，那么需要增加1个引号。
//      引号内不进行断句
        int[] mod = new int[str.length()];
        boolean wait_close = false;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (match(MARK_QUOTATION, c)) {
                int size = array_quote.size();

                //        把“xxx”、“yy”合并为“xxx_yy”进行处理
                if (size > 0) {
                    int quote_pre = array_quote.get(size - 1);
                    if (i - quote_pre == 2) {
                        if (match("，、,/", str.charAt(i - 1))) {
                            string.setCharAt(i, '“');
                            string.setCharAt(i - 2, '”');
                            array_quote.remove(size - 1);
                            mod[size - 1] = 1;
                            mod[size] = -1;
                            continue;
                        }
                    }
                }
                array_quote.add(i);

                //  为xxx：“xxx”做标记
                if (i > 1) {
                    // 当前发言的正引号的前一个字符
                    char char_b1=str.charAt(i - 1);
                    // 上次发言的正引号的前一个字符
                    char char_b2=0;
                    if (match(MARK_QUOTATION_BEFORE, char_b1)) {
                        // 如果不是第一处引号，寻找上一处断句，进行分段
                        if (array_quote.size() > 1) {
                            int last_quote = array_quote.get(array_quote.size() - 2);
                            int p=0;
                            if(char_b1==',' || char_b1=='，'){
                                if(array_quote.size()>2){
                                    p=array_quote.get(array_quote.size()-3);
                                    if(p>0){
                                        char_b2=str.charAt(p-1);
                                    }
                                }
                            }
//                            if(char_b2=='.' || char_b2=='。')
                            if(match(MARK_SENTENCES_END_P,char_b2))
                                ins_n.add(p-1);
                            else{
                                int last_end = seekLast(str, MARK_SENTENCES_END, i, last_quote);
                                if (last_end > 0)
                                    ins_n.add(last_end);
                                else
                                    ins_n.add(last_quote);
                            }
                        }

                        wait_close = true;
                        mod[size] = 1;
                        if (size > 0) {
                            mod[size - 1] = -1;
                            if (size > 1) {
                                mod[size - 2] = 1;
                            }

/*
                            int quote_pre = array_quote.get(array_quote.size() - 2);
                            boolean flag_ins_n = false;
                            for (int j = i; j > quote_pre; j--) {
                                if (match(MARK_SENTENCES_END, string.charAt(j))) {
                                    ins_n.add(j);
                                    flag_ins_n = true;
                                }
                            }
                            if (!flag_ins_n)
                                ins_n.add(quote_pre);
                            */
                        }
                    } else if (wait_close) {
                        {
                            wait_close = false;
                            ins_n.add(i);
                        }
                    }
                }

            }
        }

        int size = array_quote.size();


//        标记循环状态，此位置前的引号是否已经配对
        boolean opend = false;
        if (size > 0) {

//        第1次遍历array_quote，令其元素的值不为0
            for (int i = 0; i < size; i++) {
                if (mod[i] > 0) {
                    opend = true;
                } else if (mod[i] < 0) {
//                连续2个反引号表明存在冲突，强制把前一个设为正引号
                    if (!opend) {
                        if (i > 0)
                            mod[i] = 3;
                    }
                    opend = false;
                } else {
                    opend = !opend;
                    if (opend)
                        mod[i] = 2;
                    else
                        mod[i] = -2;
                }
            }
//        修正，断尾必须封闭引号
            if (opend) {
                if (array_quote.get(size - 1) - string.length() > -3) {
//            if((match(MARK_QUOTATION,string.charAt(string.length()-1)) || match(MARK_QUOTATION,string.charAt(string.length()-2)))){
                    if(size>1)
                        mod[size - 2] = 4;
                    // 0<=i<size,故无需判断size>=1
                    mod[size - 1] = -4;
                } else if (!match(MARK_SENTENCES_SAY, string.charAt(string.length() - 2)))
                    string.append("”");
            }


//      第2次循环，mod[i]由负变正时，前1字符如果是句末，需要插入换行
            int loop2_mod_1 = -1; //上一个引号跟随内容的状态
            int loop2_mod_2; //当前引号跟随内容的状态
            int i = 0;
            int j = array_quote.get(0) - 1; //当前引号前一字符的序号
            if (j < 0) {
                i = 1;
                loop2_mod_1 = 0;
            }

            for (; i < size; i++) {
                j = array_quote.get(i) - 1;
                loop2_mod_2 = mod[i];
                if (loop2_mod_1 < 0 && loop2_mod_2 > 0) {
                    if (match(MARK_SENTENCES_END, string.charAt(j)))
                        ins_n.add(j);
                }
/*                else if (mod[i - 1] > 0 && mod[i] < 0) {
                    if (j > 0) {
                        if (match(MARK_SENTENCES_END, string.charAt(j)))
                            ins_n.add(j);
                    }
                }
*/
                loop2_mod_1 = loop2_mod_2;
            }
        }

//        第3次循环，匹配并插入换行。
//        "xxxx" xxxx。\n xxx“xxxx”
//        未实现

//        随机在句末插入换行符
        ins_n = new ArrayList<Integer>(new HashSet<Integer>(ins_n));
        Collections.sort(ins_n);

        {
            String subs="";
            int j=0;
            int progress = 0;

            int next_line = -1;
            if (ins_n.size() > 0)
                next_line = ins_n.get(j);

            int gain = 3;
            int min=0;
            int trigger=2;

            for (int i = 0; i < array_quote.size(); i++) {
                int qutoe = array_quote.get(i);
                if(qutoe>0){
                    gain=4;
                    min=2;
                    trigger=4;
                }else{
                    gain = 3;
                    min=0;
                    trigger=2;
                }

//            把引号前的换行符与内容相间插入
                for (; j < ins_n.size(); j++) {
//                如果下一个换行符在当前引号前，那么需要此次处理.如果紧挨当前引号，需要考虑插入引号的情况
                    if (next_line >= qutoe)
                        break;
                    next_line = ins_n.get(j);
                    if(progress<next_line){
                        subs=string.substring(progress, next_line);
                        ins_n.addAll(forceSplit(subs,progress,min,gain,trigger));
                        progress = next_line + 1;
                    }
                }
                if (progress < qutoe) {
                    subs=string.substring(progress, qutoe + 1);
                    ins_n.addAll(forceSplit(subs,progress,min,gain,trigger));
                    progress = qutoe + 1;
                }
            }


            for (; j < ins_n.size(); j++) {
                next_line = ins_n.get(j);
                if(progress<next_line) {
                    subs = string.substring(progress, next_line);
                    ins_n.addAll(forceSplit(subs, progress, min, gain, trigger));
                    progress = next_line + 1;
                }
            }

            if (progress < string.length()) {
                subs=string.substring(progress, string.length());
                ins_n.addAll(forceSplit(subs,progress,min,gain,trigger));
            }

        }

//     根据段落状态修正引号方向、计算需要插入引号的位置
//     ins_quote跟随array_quote   ins_quote[i]!=0,则array_quote.get(i)的引号前需要前插入'”'
        boolean[] ins_quote = new boolean[size];
        opend = false;
        for (int i = 0; i < size; i++) {
            int p = array_quote.get(i);
            if (mod[i] > 0) {
                string.setCharAt(p, '“');
                if (opend)
                    ins_quote[i] = true;
                opend = true;
            } else if (mod[i] < 0) {
                string.setCharAt(p, '”');
                opend = false;
            } else {
                opend = !opend;
                if (opend)
                    string.setCharAt(p, '“');
                else
                    string.setCharAt(p, '”');
            }
        }

        ins_n = new ArrayList<Integer>(new HashSet<Integer>(ins_n));
        Collections.sort(ins_n);

//        输出log进行检验
/*
        System.out.println("quote[i]:position/mod\t" + string);
        for (int i = 0; i < array_quote.size(); i++) {
            System.out.print(" [" + i + "]" + array_quote.get(i) + "/" + mod[i]);
        }
        System.out.print("\n");

        System.out.print("ins_q:");
        for (int i = 0; i < ins_quote.length; i++) {
            System.out.print(" " + ins_quote[i]);
        }
        System.out.print("\n");

        System.out.print("ins_n:");

        for (int i : ins_n) {
            System.out.print(" " + i);
        }
        System.out.print("\n");
*/

//     完成字符串拼接（从string复制、插入引号和换行
//     ins_quote 在引号前插入一个引号。   ins_quote[i]!=0,则array_quote.get(i)的引号前需要前插入'”'
//     ins_n 插入换行。数组的值表示插入换行符的位置
        StringBuffer buffer = new StringBuffer((int) (str.length() * 1.15));

        int j = 0;
        int progress = 0;

        int next_line = -1;
        if (ins_n.size() > 0)
            next_line = ins_n.get(j);

        for (int i = 0; i < array_quote.size(); i++) {
            int qutoe = array_quote.get(i);

//            把引号前的换行符与内容相间插入
            for (; j < ins_n.size(); j++) {
//                如果下一个换行符在当前引号前，那么需要此次处理.如果紧挨当前引号，需要考虑插入引号的情况
                if (next_line >= qutoe)
                    break;
                next_line = ins_n.get(j);
                buffer.append(string, progress, next_line + 1);
                buffer.append('\n');
                progress = next_line + 1;
            }
            if (progress < qutoe) {
                buffer.append(string, progress, qutoe + 1);
                progress = qutoe + 1;
            }
            if (ins_quote[i] && buffer.length() > 2) {
                if (buffer.charAt(buffer.length() - 1) == '\n')
                    buffer.append('“');
                else
                    buffer.insert(buffer.length() - 1, "”\n");
            }
        }

        for (; j < ins_n.size(); j++) {
            next_line = ins_n.get(j);
            if(progress<=next_line){
                buffer.append(string, progress, next_line + 1);
                buffer.append('\n');
                progress = next_line + 1;
            }
        }

        if (progress < string.length()) {
            buffer.append(string, progress, string.length());
        }

        return buffer.toString();
    }

    /**
     * 计算匹配到字典的每个字符的位置
     *
     * @param str     待匹配的字符串
     * @param key     字典
     * @param from    从字符串的第几个字符开始匹配
     * @param to      匹配到第几个字符结束
     * @param inOrder 是否按照从前向后的顺序匹配
     * @return 返回距离构成的ArrayList<Integer>
     */
    private static ArrayList<Integer> seekIndexs(String str, String key, int from, int to, boolean inOrder) {
        ArrayList<Integer> list = new ArrayList<>();

        if (str.length() - from < 1)
            return list;
        int i = 0;
        if (from > i)
            i = from;
        int t = str.length();
        if (to > 0)
            t = Math.min(t, to);
        char c;
        for (; i < t; i++) {
            if (inOrder)
                c = str.charAt(i);
            else
                c = str.charAt(str.length() - i - 1);
            if (key.indexOf(c) != -1) {
                list.add(i);
            }
        }
        return list;
    }


    /**
     * 计算字符串最后出现与字典中字符匹配的位置
     *
     * @param str  数据字符串
     * @param key  字典字符串
     * @param from 从哪个字符开始匹配，默认0
     * @param to   匹配到哪个字符（不包含此字符）默认匹配到最末位
     * @return 位置（正向计算)
     */
    private static int seekLast(String str, String key, int from, int to) {
        if (str.length() - from < 1)
            return -1;
        int i = 0;
        if (from > i)
            i = from;
        int t = 0;
        if (to > 0)
            t = to;
        char c;
        for (; i > t; i--) {
            c = str.charAt(i);
            if (key.indexOf(c) != -1) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 计算字符串与字典中字符的最短距离
     *
     * @param str     数据字符串
     * @param key     字典字符串
     * @param from    从哪个字符开始匹配，默认0
     * @param to      匹配到哪个字符（不包含此字符）默认匹配到最末位
     * @param inOrder 是否从正向开始匹配
     * @return 返回最短距离, 注意不是str的char的下标
     */
    private static int seekIndex(String str, String key, int from, int to, boolean inOrder) {
        if (str.length() - from < 1)
            return -1;
        int i = 0;
        if (from > i)
            i = from;
        int t = str.length();
        if (to > 0)
            t = Math.min(t, to);
        char c;
        for (; i < t; i++) {
            if (inOrder)
                c = str.charAt(i);
            else
                c = str.charAt(str.length() - i - 1);
            if (key.indexOf(c) != -1) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 计算字符串与字典的距离。
     *
     * @param str     数据字符串
     * @param form    从第几个字符开始匹配
     * @param to      匹配到第几个字符串结束
     * @param inOrder 是否从前向后匹配。
     * @param words   可变长参数构成的字典。每个字符串代表一个字符
     * @return 匹配结果。注意这个距离是使用第一个字符进行计算的
     */
    private static int seekWordsIndex(String str, int form, int to, boolean inOrder, String... words) {

        if (words.length < 1)
            return -2;

        int i = seekIndex(str, words[0], form, to, inOrder);
        if (i < 0)
            return i;

        for (int j = 1; j < words.length; j++) {
            int k = seekIndex(str, words[j], form, to, inOrder);
            if (inOrder) {
                if (i + j != k)
                    return -3;
            } else {
                if (i - j != k)
                    return -3;
            }
        }
        return i;
    }

    /* 搜寻引号并进行分段。处理了一、二、五三类常见情况
    参照百科词条[引号#应用示例](https://baike.baidu.com/item/%E5%BC%95%E5%8F%B7/998963?#5)对引号内容进行矫正并分句。
    一、完整引用说话内容，在反引号内侧有断句标点。例如：
            1) 丫姑折断几枝扔下来，边叫我的小名儿边说：“先喂饱你！”
            2）“哎呀，真是美极了！”皇帝说，“我十分满意！”
            3）“怕什么！海的美就在这里！”我说道。
    二、部分引用，在反引号外侧有断句标点：
            4）适当地改善自己的生活，岂但“你管得着吗”，而且是顺乎天理，合乎人情的。
            5）现代画家徐悲鸿笔下的马，正如有的评论家所说的那样，“形神兼备，充满生机”。
            6）唐朝的张嘉贞说它“制造奇特，人不知其所为”。
    三、一段接着一段地直接引用时，中间段落只在段首用起引号，该段段尾却不用引回号。但是正统文学不在考虑范围内。
    四、引号里面又要用引号时，外面一层用双引号，里面一层用单引号。暂时不需要考虑
    五、反语和强调，周围没有断句符号。
*/

    //  段落换行符
    private static String SPACE_BEFORE_PARAGRAPH = "\n    ";
    //  段落末位的标点
    private static String MARK_SENTENCES = "？。！?!~”\"";
    //  句子结尾的标点。因为引号可能存在误判，不包含引号。
    private static String MARK_SENTENCES_END = "？。！?!~";
    private static String MARK_SENTENCES_END_P = ".？。！?!~";
    //  句中标点，由于某些网站常把“，”写为"."，故英文句点按照句中标点判断
    private static String MARK_SENTENCES_MID = ".，、,—…";
    private static String MARK_SENTENCES_F = "啊嘛吧吗噢哦了呢呐";
    private static String MARK_SENTENCES_SAY = "问说喊唱叫骂道着答";
    //  XXX说：“”的冒号
    private static String MARK_QUOTATION_BEFORE = "，：,:";
    //  引号
    private static String MARK_QUOTATION = "\"“”";

    private static String PARAGRAPH_DIAGLOG = "^[\"”“][^\"”“]+[\"”“]$";

    private static boolean isFullSentences(String s) {
        if (s.length() < 2)
            return false;
        char c = s.charAt(s.length() - 1);
        return MARK_SENTENCES.indexOf(c) != -1;
    }

    private static boolean match(String rule, char chr) {
        return rule.indexOf(chr) != -1;
    }


    private boolean isUseTo(String useTo, String bookTag, String bookName) {
        return TextUtils.isEmpty(useTo)
                || useTo.contains(bookTag)
                || useTo.contains(bookName);
    }

}
