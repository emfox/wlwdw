package com.wlwdw.gps1s;

import com.wlwdw.gps1s.service.LocService;
import com.wlwdw.gps1s.service.Utils;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.baidu.location.LocationClient;


import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private final int SDK_PERMISSION_REQUEST = 127;
    private ListView FunctionList;

    private final int DIALOG_KEY_BACK = 1;
    private final int DIALOG_PERMISSION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        //创建弹窗显示隐私政策
        if (!Utils.contains(this, Utils.SP_PRIVACY_DIALOG)) {
            createPrivacyDialog();
        } else {
            boolean status = Utils.getString(MainActivity.this, Utils.SP_PRIVACY_STATUS).equals("1");
            initSDK(status);
        }

        FunctionList = (ListView) findViewById(R.id.functionList);
        FunctionList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1, getData()));
    }

    private void initSDK(boolean status) {
        LocationClient.setAgreePrivacy(status);
        ((LocationApplication)getApplication()).locService = new LocService(getApplicationContext());

        onUsePermission();
        getPersimmions();
    }

    private void createPrivacyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String notifyString = "为进一步加强对最终用户个人信息的安全保护措施, 请仔细阅读如下隐私政策并确认是否同意：\n《服务隐私政策》";
        SpannableStringBuilder spannableString = new SpannableStringBuilder(notifyString);
        Pattern pattern = Pattern.compile("《服务隐私政策》");
        Matcher matcher = pattern.matcher(spannableString);
        while (matcher.find()) {
            setClickableSpan(spannableString, matcher);
        }

        View view = View.inflate(this, R.layout.notify_privacy_text, null);
        TextView notifyText = (TextView) view.findViewById(R.id.notify_text);
        notifyText.setText(spannableString);
        notifyText.setMovementMethod(LinkMovementMethod.getInstance());

        builder.setView(view);
        builder.setPositiveButton("同意", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                initSDK(true);
                Utils.putString(MainActivity.this, Utils.SP_PRIVACY_DIALOG, Utils.SP_PRIVACY_DIALOG);
                Utils.putString(MainActivity.this, Utils.SP_PRIVACY_STATUS, "1");
            }
        });

        builder.setNegativeButton("不同意", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                initSDK(false);
                Utils.putString(MainActivity.this, Utils.SP_PRIVACY_STATUS, "0");
            }
        });


        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) positiveButton.getLayoutParams();
        layoutParams.weight = 10;
        positiveButton.setLayoutParams(layoutParams);
        negativeButton.setLayoutParams(layoutParams);
    }

    private void setClickableSpan(SpannableStringBuilder span, Matcher matcher) {
        int start = matcher.start();
        int end = matcher.end();

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                final Uri uri = Uri.parse("https://lbsyun.baidu.com/index.php?title=openprivacy");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }

            @Override
            public void updateDrawState(TextPaint textPaint) {
                textPaint.setUnderlineText(false);
            }
        };

        span.setSpan(new ForegroundColorSpan(Color.CYAN), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(clickableSpan, start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    @TargetApi(23)
    private void getPersimmions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissions = new ArrayList<String>();
            // Foreground location (GPS + network). If denied it is re-asked on
            // the next launch, since locating the device is the app's purpose.
            // The background-location grant is requested from LocationActivity
            // when the user actually starts continuous tracking (Android 10+).
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (permissions.size() > 0) {
                requestPermissions(permissions.toArray(new String[permissions.size()]), SDK_PERMISSION_REQUEST);
            }
        }
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // TODO Auto-generated method stub
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        FunctionList.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub
                Class<?> TargetClass = null;
                switch (arg2) {
                    case 0:
                        TargetClass = LocationActivity.class;
                        break;
                    default:
                        break;
                }
                if (TargetClass != null) {
                    Intent intent = new Intent(MainActivity.this, TargetClass);
                    intent.putExtra("from", 0);
                    startActivity(intent);
                }
            }
        });
    }

    private List<String> getData() {
        List<String> data = new ArrayList<String>();
        data.add("进入定位功能");
        return data;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!Utils.contains(this, Utils.SP_KEY_BACK_RETURN)) {
                Utils.putString(this, Utils.SP_KEY_BACK_RETURN, Utils.SP_KEY_BACK_RETURN);
                showMissingPermissionDialog("提示", getString(R.string.action_background), DIALOG_KEY_BACK);
                return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 显示提示信息
     */
    private void showMissingPermissionDialog(String title, String message, final int type) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);

        builder.setPositiveButton("确定",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (type == DIALOG_KEY_BACK) {
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addCategory(Intent.CATEGORY_HOME);
                            startActivity(intent);
                        } else {
                            dialog.dismiss();
                        }

                    }
                });

        builder.setCancelable(false);

        builder.show();
    }

    private void onUsePermission() {
        if (!Utils.contains(this, Utils.SP_PERMISSION_DIALOG)) {
            Utils.putString(this, Utils.SP_PERMISSION_DIALOG, Utils.SP_PERMISSION_DIALOG);
            showMissingPermissionDialog("本应用需要使用定位权限", "定位权限用于确定并上报设备位置。\n开始持续定位（熄屏追踪）时，还会申请后台定位权限。\n", DIALOG_PERMISSION);
        }
    }
}
