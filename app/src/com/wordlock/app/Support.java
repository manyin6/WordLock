package com.wordlock.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.widget.Toast;

/** 微信客服: copy the WeChat ID and offer to open WeChat. */
public class Support {
    static void wechat(Activity a) {
        try { a.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("微信客服", Docs.WECHAT)); } catch (Exception e) { }
        Toast.makeText(a, "已复制微信号 " + Docs.WECHAT, Toast.LENGTH_SHORT).show();
        new AlertDialog.Builder(a).setTitle("微信客服")
                .setMessage("微信号：" + Docs.WECHAT + "（已复制）\n\n打开微信 → 右上角「＋」→「添加朋友」→ 粘贴搜索，添加后发消息即可。")
                .setPositiveButton("打开微信", (d, i) -> {
                    Intent li = a.getPackageManager().getLaunchIntentForPackage("com.tencent.mm");
                    if (li == null) { Toast.makeText(a, "没有找到微信", Toast.LENGTH_SHORT).show(); return; }
                    try { a.startActivity(li); } catch (Exception e) { Toast.makeText(a, "打不开微信", Toast.LENGTH_SHORT).show(); }
                })
                .setNegativeButton("好", null).show();
    }
}
