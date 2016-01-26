package com.wobiancao.getluckymomenyeasy.service;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xy on 16/1/26.
 */
public class HouSaiLeiService extends AccessibilityService {
    //qq
    private static final String QQ_OPEN_EN = "Open";
    private static final String QQ_OPENED_EN = "You've opened";
    private final static String QQ_DEFAULT_CLICK_OPEN = "点击拆开";
    private final static String QQ_HONG_BAO_PASSWORD = "口令红包";
    private final static String QQ_HONG_BAO_PASSWORD_OPENED = "口令红包已拆开";
    private final static String QQ_CLICK_TO_PASTE_PASSWORD = "点击输入口令";
    private final static String QQ_NOTIFICATION_TIP = "[QQ红包]";
    private boolean mLuckyMoneyReceived;
    private String lastFetchedHongbaoId = null;
    private long lastFetchedTime = 0;
    private static final int MAX_CACHE_TOLERANCE = 5000;
    private AccessibilityNodeInfo rootNodeInfo;
    private List<AccessibilityNodeInfo> mReceiveNode;
    //wechat
    private static final String WECHAT_DETAILS_EN = "Details";
    private static final String WECHAT_DETAILS_CH = "红包详情";
    private static final String WECHAT_BETTER_LUCK_EN = "Better luck next time!";
    private static final String WECHAT_BETTER_LUCK_CH = "手慢了";
    private static final String WECHAT_EXPIRES_CH = "红包已失效";
    private static final String WECHAT_VIEW_SELF_CH = "查看红包";
    private static final String WECHAT_VIEW_OTHERS_CH = "领取红包";
    private final static String WECHAT_NOTIFICATION_TIP = "[微信红包]";
    private boolean mMutex = false;

    private AccessibilityNodeInfo  mUnpackNode;
    private boolean mLuckyMoneyPicked, mNeedUnpack, mNeedBack;
    private String lastContentDescription = "";
    private HongbaoSignature signature = new HongbaoSignature();
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void recycle(AccessibilityNodeInfo info) {
        if (info.getChildCount() == 0) {
            if (info.getText() != null && info.getText().toString().equals(QQ_CLICK_TO_PASTE_PASSWORD)) {
                info.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            if (info.getClassName().toString().equals("android.widget.Button") && info.getText().toString().equals("发送")) {
                info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        } else {
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    recycle(info.getChild(i));
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        this.rootNodeInfo = event.getSource();
        if (rootNodeInfo == null) {
            return;
        }
        if (!mMutex) {
            if (watchNotifications(event)) {
                return;
            }
            if (watchList(event)){
                return;
            }
        }
        mReceiveNode = null;
        checkNodeInfo();
        /* 如果已经接收到红包并且还没有戳开 */
        if (mLuckyMoneyReceived && (mReceiveNode != null)) {
            mMutex = true;
            int size = mReceiveNode.size();
            if (size > 0) {
                String id = getHongbaoText(mReceiveNode.get(size - 1));
                long now = System.currentTimeMillis();
                if (this.shouldReturn(id, now - lastFetchedTime)){
                    return;
                }
                lastFetchedHongbaoId = id;
                lastFetchedTime = now;
                AccessibilityNodeInfo cellNode = mReceiveNode.get(size - 1);
                if (cellNode.getText().toString().equals(QQ_HONG_BAO_PASSWORD_OPENED)) {
                    return;
                }
                cellNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (cellNode.getText().toString().equals(QQ_HONG_BAO_PASSWORD)) {
                    AccessibilityNodeInfo rowNode = getRootInActiveWindow();
                    if (rowNode == null) {
                        return;
                    } else {
                        recycle(rowNode);
                    }
                }
                if (cellNode.getText() != null && cellNode.getText().toString().equals(QQ_CLICK_TO_PASTE_PASSWORD)) {
                    cellNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                if (cellNode.getClassName().toString().equals("android.widget.Button") && cellNode.getText().toString().equals("发送")) {
                    cellNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                mLuckyMoneyReceived = false;
                mLuckyMoneyPicked = true;
            }
        }
        /* 如果戳开但还未领取 */
        if (mNeedUnpack && (mUnpackNode != null)) {
            AccessibilityNodeInfo cellNode = mUnpackNode;
            cellNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (cellNode.getText().toString().equals(QQ_HONG_BAO_PASSWORD)) {
                AccessibilityNodeInfo rowNode = getRootInActiveWindow();
                if (rowNode == null) {
                    return;
                } else {
                    recycle(rowNode);
                }
            }
            mNeedUnpack = false;
        }
        if (mNeedBack) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            mMutex = false;
            mNeedBack = false;
        }

    }


    /**
     * 检查节点信息
     */
    private void checkNodeInfo() {

        if (rootNodeInfo == null) {
            return;
        }

         /* 聊天会话窗口，遍历节点匹配“点击拆开”，“口令红包”，“点击输入口令” */
        List<AccessibilityNodeInfo> nodes1 = this.findAccessibilityNodeInfosByTexts(this.rootNodeInfo, new String[]{
                QQ_DEFAULT_CLICK_OPEN, QQ_HONG_BAO_PASSWORD, QQ_CLICK_TO_PASTE_PASSWORD, WECHAT_VIEW_OTHERS_CH, WECHAT_VIEW_SELF_CH, "发送"});

        if (!nodes1.isEmpty()) {
            AccessibilityNodeInfo targetNode = nodes1.get(nodes1.size() - 1);
//            String nodeId = Integer.toHexString(System.identityHashCode(this.rootNodeInfo));
            if (this.signature.generateSignature(targetNode)) {
                mLuckyMoneyReceived = true;
                mReceiveNode = nodes1;
            }
            return;
        }
        /* 戳开红包，红包还没抢完，遍历节点匹配“拆红包” */
        AccessibilityNodeInfo node2 = (this.rootNodeInfo.getChildCount() > 3) ? this.rootNodeInfo.getChild(3) : null;
        if (node2 != null && node2.getClassName().equals("android.widget.Button")) {
            mUnpackNode = node2;
            mNeedUnpack = true;
            return;
        }

        /* 戳开红包，红包已被抢完，遍历节点匹配“红包详情”和“手慢了” */
        if (mLuckyMoneyPicked) {
            List<AccessibilityNodeInfo> nodes3 = this.findAccessibilityNodeInfosByTexts(this.rootNodeInfo, new String[]{
                    WECHAT_BETTER_LUCK_CH, WECHAT_DETAILS_CH,
                    WECHAT_BETTER_LUCK_EN, WECHAT_DETAILS_EN, WECHAT_EXPIRES_CH});
            if (!nodes3.isEmpty()) {
                mNeedBack = true;
                mLuckyMoneyPicked = false;
            }
        }

    }


    /**
     * 将节点对象的id和红包上的内容合并
     * 用于表示一个唯一的红包
     *
     * @param node 任意对象
     * @return 红包标识字符串
     */
    private String getHongbaoText(AccessibilityNodeInfo node) {
        /* 获取红包上的文本 */
        String content;
        try {
            AccessibilityNodeInfo i = node.getParent().getChild(0);
            content = i.getText().toString();
        } catch (NullPointerException npe) {
            return null;
        }

        return content;
    }


    /**
     * 判断是否返回,减少点击次数
     * 现在的策略是当红包文本和缓存不一致时,戳
     * 文本一致且间隔大于MAX_CACHE_TOLERANCE时,戳
     *
     * @param id       红包id
     * @param duration 红包到达与缓存的间隔
     * @return 是否应该返回
     */
    private boolean shouldReturn(String id, long duration) {
        // ID为空
        if (id == null) return true;

        // 名称和缓存不一致
        if (duration < MAX_CACHE_TOLERANCE && id.equals(lastFetchedHongbaoId)) {
            return true;
        }

        return false;
    }

    /**
     * 批量化执行AccessibilityNodeInfo.findAccessibilityNodeInfosByText(text).
     * 由于这个操作影响性能,将所有需要匹配的文字一起处理,尽早返回
     *
     * @param nodeInfo 窗口根节点
     * @param texts    需要匹配的字符串们
     * @return 匹配到的节点数组
     */
    private List<AccessibilityNodeInfo> findAccessibilityNodeInfosByTexts(AccessibilityNodeInfo nodeInfo, String[] texts) {
        for (String text : texts) {
            if (text == null) continue;

            List<AccessibilityNodeInfo> nodes = nodeInfo.findAccessibilityNodeInfosByText(text);

            if (!nodes.isEmpty()) {
                if (text.equals(QQ_OPEN_EN) && !nodeInfo.findAccessibilityNodeInfosByText(QQ_OPENED_EN).isEmpty()) {
                    continue;
                }
                return nodes;
            }
        }
        return new ArrayList<>();
    }

    @Override
    public void onInterrupt() {

    }
    private boolean watchList(AccessibilityEvent event) {
        // Not a message
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.getSource() == null)
            return false;

        List<AccessibilityNodeInfo> nodes = event.getSource().findAccessibilityNodeInfosByText(WECHAT_NOTIFICATION_TIP);
        if (!nodes.isEmpty()) {
            AccessibilityNodeInfo nodeToClick = nodes.get(0);
            CharSequence contentDescription = nodeToClick.getContentDescription();
            if (contentDescription != null && !lastContentDescription.equals(contentDescription)) {
                nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                lastContentDescription = contentDescription.toString();
                return true;
            }
        }
        return false;
    }

    private boolean watchNotifications(AccessibilityEvent event) {
        // Not a notification
        if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return false;
        }

        // Not a hongbao
        String tip = event.getText().toString();
        if (!tip.contains(WECHAT_NOTIFICATION_TIP) && tip.contains(QQ_NOTIFICATION_TIP)){
            return true;
        }

        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            Notification notification = (Notification) parcelable;
            try {
                notification.contentIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
        return true;
    }
}
