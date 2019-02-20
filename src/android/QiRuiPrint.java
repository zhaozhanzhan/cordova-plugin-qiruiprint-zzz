package com.zzz;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;
import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import com.google.gson.*;

import com.qr.print.*;

/**
 * This class echoes a string called from JavaScript.
 */
public class QiRuiPrint extends CordovaPlugin {
    private static final String TAG = "QiRuiPrint";

    // execute是必须重写的方法，会有三个构造方法，按需重写自己需要的，
    // execute方法中的action参数是和Toast.js关联使用的，
    // args是js返回的参数，callbackContext是对js的回调
    private BluetoothAdapter mBtAdapter;

    private CallbackContext discoveryCallBack,notifyConnectState;

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG,"onStart");
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBtAdapter == null){
            return;
        }
        //获取已经绑定的蓝牙设备列表，暂时不需要
        // Get a set of currently paired devices
//        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        // If there are paired devices, add each one to the ArrayAdapter
//        if (pairedDevices.size() > 0) {
//            for (BluetoothDevice device : pairedDevices) {
//                if (mPairedDevicesArrayAdapter != null) {
//                    mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
//                }
//            }
//        } else {
//            mPairedDevicesArrayAdapter.add("没有匹配");
//        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestroy");
        // Make sure we're not doing discovery anymore
        cancelPrinterServe();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("doDiscovery")) {//启动扫描，需要手动调用终止扫描方法
            discoveryCallBack = callbackContext;
            doDiscovery();
            return true;
        }else  if (action.equals("cancelDiscovery")) {//启动扫描，需要手动调用终止扫描方法
           cancelDiscovery(callbackContext);
            return true;
        }else if(action.equals("checkBleEnable")){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("isEnabled",checkBleEnable());
            callbackContext.success(jsonObject);
            return true;
        }else if(action.equals("notifyConnectState")){//建立打印机连接状态监听
            notifyConnectState = callbackContext;
            return true;
        }else if(action.equals("connectPrinter")){//进行打印机连接
            String devName = args.getString(0);
            String devMac = args.getString(1);

            Log.d(TAG,"要连接的打印机："+devName);
            Log.d(TAG,"要连接的打印机地址："+devMac);
            connectPrinter(devName,devMac);
            return true;
        } else if(action.equals("printContent")){//打印内容
            String content = args.getString(0);
            printContent(content,callbackContext);
            return true;
        } else if(action.equals("cancelPrinterServe")){//终止打印服务，断开蓝牙打印机
            cancelPrinterServe(callbackContext);
            return true;
        }
        return false;
    }

    /**
     * 搜索设备
     */
    private void doDiscovery() {
        Log.d(TAG, "doDiscovery()");
        if (mBtAdapter == null) {
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        // If we're already discovering, stop it
        if (mBtAdapter.isDiscovering()) {
            Activity activity = this.cordova.getActivity();
            activity.unregisterReceiver(mReceiver);
            mBtAdapter.cancelDiscovery();
        }

        Activity activity = this.cordova.getActivity();
        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        activity.registerReceiver(mReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        activity.registerReceiver(mReceiver, filter);

        // Request discover from BluetoothAdapter
        mBtAdapter.startDiscovery();
    }

    /**
     * 终止蓝牙扫描
     */
    private void cancelDiscovery(CallbackContext callbackContext){
        // Unregister broadcast listeners
        Activity activity = this.cordova.getActivity();
        activity.unregisterReceiver(mReceiver);

        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        if(callbackContext != null){
            callbackContext.success();
        }
    }

    //建立广播接收蓝牙扫描结果
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, action);
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                if (discoveryCallBack != null) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.d(TAG, "扫描到：" + device.getName());
                    Log.d(TAG, "扫描到：" + device.getAddress());
                    // If it's already paired, skip it, because it's been listed already
                    JSONObject deviceJson = new JSONObject();
                    try {
                        deviceJson.put("name", device.getName());
                        deviceJson.put("address", device.getAddress());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, deviceJson);
                    pluginResult.setKeepCallback(true);
                    discoveryCallBack.sendPluginResult(pluginResult);
                }
            }
        }
    };

    /**
     * 检测蓝牙是否开启
     */
    private boolean checkBleEnable(){
        //若对象为空，则需要重新获取蓝牙对象；
        if (mBtAdapter == null) {
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        //若设备不支持蓝牙，则为null
        if (mBtAdapter == null) {
           return false;
        }

        return mBtAdapter.isEnabled();
    }

    /**
     * 启瑞打印机
     */
    private BluetoothAdapter mBluetoothAdapter = null;
    private PrintPP_CPCL printPP_cpcl;
    private boolean isConnectedQirui = false;

    /**
     * 断开启瑞打印机
     */
    private void cancelPrinterServe(){
        if(printPP_cpcl != null){
            printPP_cpcl.disconnect();
            printPP_cpcl = null;
            notifyJSConnectInfo("","");
        }
    }

    /**
     * 断开启瑞打印机
     */
    private void cancelPrinterServe(CallbackContext callbackContext){
        cancelPrinterServe();
        if(callbackContext != null){
            callbackContext.success();
        }
    }

    /**
     * 通知JS更新打印机信息
     * 若连接上则返回打印机名称和mac地址
     * @param name
     * @param mac
     */
    private void notifyJSConnectInfo(String name, String mac){
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name",name);
            jsonObject.put("address",mac);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, jsonObject);
        pluginResult.setKeepCallback(true);
        notifyConnectState.sendPluginResult(pluginResult);
    }


    /**
     * 根据打印机名称和地址连接打印机
     * @param connectedName
     * @param connectedMac
     */
    private void connectPrinter(String connectedName,String connectedMac) {
        printPP_cpcl = new PrintPP_CPCL();
        if (isConnectedQirui & (printPP_cpcl != null)) {
            printPP_cpcl.disconnect();
            printPP_cpcl = null;
            isConnectedQirui = false;
            notifyJSConnectInfo("","");
        }
        if (!isConnectedQirui) {
            connectPrinter_Android(connectedName, connectedMac);
        }
    }

    /**
     * 安卓层进行连接打印机
     * @param name
     * @param mac
     */
    private void connectPrinter_Android(final String name, final String mac) {
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                boolean state = msg.getData().getBoolean("state");
                if (state) {
                    String name = msg.getData().getString("name");
                    String mac = msg.getData().getString("mac");

                    Log.d(TAG,"===========打印机连接成功");
                    notifyJSConnectInfo(name,mac);
                }
                isConnectedQirui = state;
            }
        };

        new Thread() {
            public void run() {
                if (printPP_cpcl == null){
                    printPP_cpcl = new PrintPP_CPCL();
                }
                boolean state = printPP_cpcl.connect(name, mac);
                Message msg = new Message();
                Bundle bundle = new Bundle();
                bundle.putString("name", name);
                bundle.putString("mac", mac);
                bundle.putBoolean("state", state);
                msg.setData(bundle);
                if(!state){
                    notifyJSConnectInfo("","");
                    if(mBluetoothAdapter != null){
                        mBluetoothAdapter.disable();
                    }
                }
                handler.sendMessage(msg);
            }
        }.start();
    }

    /**
     * 进行内容打印
     * @param content
     */
    private void printContent(String content,CallbackContext callbackContext){
        PrintLabelBean printLabelBean = new Gson().fromJson(content, PrintLabelBean.class);
        if (printPP_cpcl == null){
            Toast.makeText(this.cordova.getContext(),"请先连接打印机！", Toast.LENGTH_SHORT).show();
            return ;
        }
        if (printLabelBean == null){
            Toast.makeText(this.cordova.getContext(),"打印机传参有误！", Toast.LENGTH_SHORT).show();
            return ;
        }
        PrintLabelBean.SetupBean setupBean = printLabelBean.getSetup();
        if (setupBean == null){
            Toast.makeText(this.cordova.getContext(),"打印机传参有误！", Toast.LENGTH_SHORT).show();
            return ;
        }
        printPP_cpcl.pageSetup(setupBean.getPageWidth(),setupBean.getPageHeight());

        List<PrintLabelBean.DrawBarCodeBean> drawBarCodeBeanList = printLabelBean.getDrawBarCode();

        if (drawBarCodeBeanList != null){
            for (int i = 0; i < printLabelBean.getDrawBarCode().size(); i++){
                PrintLabelBean.DrawBarCodeBean drawBarCodeBean = printLabelBean.getDrawBarCode().get(i);
                printPP_cpcl.drawBarCode(drawBarCodeBean.getStart_x(),drawBarCodeBean.getStart_y(),
                        drawBarCodeBean.getText(),drawBarCodeBean.getType(),drawBarCodeBean.getRotate(),
                        drawBarCodeBean.getLinewidth(),drawBarCodeBean.getHeight());
            }
        }

        List<PrintLabelBean.DrawBoxBean> drawBoxBeanList = printLabelBean.getDrawBox();
        if(drawBoxBeanList != null){
            for (int i = 0; i < printLabelBean.getDrawBox().size(); i++){
                PrintLabelBean.DrawBoxBean drawBoxBean = printLabelBean.getDrawBox().get(i);
                printPP_cpcl.drawBox(drawBoxBean.getLineWidth(),drawBoxBean.getTop_left_x(),
                        drawBoxBean.getTop_left_y(),drawBoxBean.getBottom_right_x(),
                        drawBoxBean.getBottom_right_y());
            }
        }

        List<PrintLabelBean.DrawQrCodeBean> drawQrCodeBeanList = printLabelBean.getDrawQrCode();
        if(drawQrCodeBeanList != null){
            for (int i = 0; i < printLabelBean.getDrawQrCode().size(); i++){
                PrintLabelBean.DrawQrCodeBean drawQrCodeBean = printLabelBean.getDrawQrCode().get(i);
                printPP_cpcl.drawQrCode(drawQrCodeBean.getStart_x(),drawQrCodeBean.getStart_y(),
                        drawQrCodeBean.getText(),drawQrCodeBean.getRotate(),drawQrCodeBean.getVer(),drawQrCodeBean.getLel());
            }
        }

        List<PrintLabelBean.DrawTextBean> drawTextBeanList = printLabelBean.getDrawText();
        if(drawTextBeanList != null){
            for (int i = 0; i < printLabelBean.getDrawText().size(); i++){
                PrintLabelBean.DrawTextBean drawText = printLabelBean.getDrawText().get(i);
                printPP_cpcl.drawText(drawText.getText_x(),drawText.getText_y(),drawText.getText(),
                        drawText.getFontSize(),drawText.getRotate(),drawText.getBold(),
                        drawText.isReverse(),drawText.isUnderline());
            }
        }

        PrintLabelBean.PrintBean printBean = printLabelBean.getPrint();
        if (printBean == null){
            Toast.makeText(this.cordova.getContext(),"打印机传参有误！", Toast.LENGTH_SHORT).show();
            return ;
        }
        printPP_cpcl.print(printBean.getHorizontal(),printBean.getSkip());
        callbackContext.success();
    }

    class PrintLabelBean {

        /**
         * setup : {"pageWidth":586,"pageHeight":357}
         * drawBox : [{"lineWidth":2,"top_left_x":0,"top_left_y":0,"bottom_right_x":586,"bottom_right_y":350}]
         * drawText : [{"text_x":12,"text_y":26,"width":586,"height":33,"text":"单号：12345678","fontSize":3,"rotate":0,"bold":0,"underline":false,"reverse":false},{"text_x":12,"text_y":70,"width":348,"height":33,"text":"2018-06-04 19:39:48","fontSize":2,"rotate":0,"bold":1,"underline":false,"reverse":false},{"text_x":12,"text_y":70,"width":348,"height":33,"text":"2018-06-04 19:39:48","fontSize":2,"rotate":0,"bold":1,"underline":false,"reverse":false}]
         * drawBarCode : [{"start_x":12,"start_y":95,"text":"yunqi:1231231231321","type":9,"rotate":0,"linewidth":2,"height":75}]
         * drawQrCode : [{"start_x":404,"start_y":59,"text":"http://weixin.qq.com/r/czj27qvE8vaNre_N921h","rotate":0,"ver":3,"lel":2}]
         * print : {"horizontal":0,"skip":1}
         */

        private SetupBean setup;
        private PrintBean print;
        private List<DrawBoxBean> drawBox;
        private List<DrawTextBean> drawText;
        private List<DrawBarCodeBean> drawBarCode;
        private List<DrawQrCodeBean> drawQrCode;

        public SetupBean getSetup() {
            return setup;
        }

        public void setSetup(SetupBean setup) {
            this.setup = setup;
        }

        public PrintBean getPrint() {
            return print;
        }

        public void setPrint(PrintBean print) {
            this.print = print;
        }

        public List<DrawBoxBean> getDrawBox() {
            return drawBox;
        }

        public void setDrawBox(List<DrawBoxBean> drawBox) {
            this.drawBox = drawBox;
        }

        public List<DrawTextBean> getDrawText() {
            return drawText;
        }

        public void setDrawText(List<DrawTextBean> drawText) {
            this.drawText = drawText;
        }

        public List<DrawBarCodeBean> getDrawBarCode() {
            return drawBarCode;
        }

        public void setDrawBarCode(List<DrawBarCodeBean> drawBarCode) {
            this.drawBarCode = drawBarCode;
        }

        public List<DrawQrCodeBean> getDrawQrCode() {
            return drawQrCode;
        }

        public void setDrawQrCode(List<DrawQrCodeBean> drawQrCode) {
            this.drawQrCode = drawQrCode;
        }

        public class SetupBean {
            /**
             * pageWidth : 586
             * pageHeight : 357
             */

            private int pageWidth;
            private int pageHeight;

            public int getPageWidth() {
                return pageWidth;
            }

            public void setPageWidth(int pageWidth) {
                this.pageWidth = pageWidth;
            }

            public int getPageHeight() {
                return pageHeight;
            }

            public void setPageHeight(int pageHeight) {
                this.pageHeight = pageHeight;
            }
        }

        public class PrintBean {
            /**
             * horizontal : 0
             * skip : 1
             */

            private int horizontal;
            private int skip;

            public int getHorizontal() {
                return horizontal;
            }

            public void setHorizontal(int horizontal) {
                this.horizontal = horizontal;
            }

            public int getSkip() {
                return skip;
            }

            public void setSkip(int skip) {
                this.skip = skip;
            }
        }

        public class DrawBoxBean {
            /**
             * lineWidth : 2
             * top_left_x : 0
             * top_left_y : 0
             * bottom_right_x : 586
             * bottom_right_y : 350
             */

            private int lineWidth;
            private int top_left_x;
            private int top_left_y;
            private int bottom_right_x;
            private int bottom_right_y;

            public int getLineWidth() {
                return lineWidth;
            }

            public void setLineWidth(int lineWidth) {
                this.lineWidth = lineWidth;
            }

            public int getTop_left_x() {
                return top_left_x;
            }

            public void setTop_left_x(int top_left_x) {
                this.top_left_x = top_left_x;
            }

            public int getTop_left_y() {
                return top_left_y;
            }

            public void setTop_left_y(int top_left_y) {
                this.top_left_y = top_left_y;
            }

            public int getBottom_right_x() {
                return bottom_right_x;
            }

            public void setBottom_right_x(int bottom_right_x) {
                this.bottom_right_x = bottom_right_x;
            }

            public int getBottom_right_y() {
                return bottom_right_y;
            }

            public void setBottom_right_y(int bottom_right_y) {
                this.bottom_right_y = bottom_right_y;
            }
        }

        public class DrawTextBean {
            /**
             * text_x : 12
             * text_y : 26
             * width : 586
             * height : 33
             * text : 单号：12345678
             * fontSize : 3
             * rotate : 0
             * bold : 0
             * underline : false
             * reverse : false
             */

            private int text_x;
            private int text_y;
            private int width;
            private int height;
            private String text;
            private int fontSize;
            private int rotate;
            private int bold;
            private boolean underline;
            private boolean reverse;

            public int getText_x() {
                return text_x;
            }

            public void setText_x(int text_x) {
                this.text_x = text_x;
            }

            public int getText_y() {
                return text_y;
            }

            public void setText_y(int text_y) {
                this.text_y = text_y;
            }

            public int getWidth() {
                return width;
            }

            public void setWidth(int width) {
                this.width = width;
            }

            public int getHeight() {
                return height;
            }

            public void setHeight(int height) {
                this.height = height;
            }

            public String getText() {
                return text;
            }

            public void setText(String text) {
                this.text = text;
            }

            public int getFontSize() {
                return fontSize;
            }

            public void setFontSize(int fontSize) {
                this.fontSize = fontSize;
            }

            public int getRotate() {
                return rotate;
            }

            public void setRotate(int rotate) {
                this.rotate = rotate;
            }

            public int getBold() {
                return bold;
            }

            public void setBold(int bold) {
                this.bold = bold;
            }

            public boolean isUnderline() {
                return underline;
            }

            public void setUnderline(boolean underline) {
                this.underline = underline;
            }

            public boolean isReverse() {
                return reverse;
            }

            public void setReverse(boolean reverse) {
                this.reverse = reverse;
            }
        }

        public class DrawBarCodeBean {
            /**
             * start_x : 12
             * start_y : 95
             * text : yunqi:1231231231321
             * type : 9
             * rotate : 0
             * linewidth : 2
             * height : 75
             */

            private int start_x;
            private int start_y;
            private String text;
            private int type;
            private int rotate;
            private int linewidth;
            private int height;

            public int getStart_x() {
                return start_x;
            }

            public void setStart_x(int start_x) {
                this.start_x = start_x;
            }

            public int getStart_y() {
                return start_y;
            }

            public void setStart_y(int start_y) {
                this.start_y = start_y;
            }

            public String getText() {
                return text;
            }

            public void setText(String text) {
                this.text = text;
            }

            public int getType() {
                return type;
            }

            public void setType(int type) {
                this.type = type;
            }

            public int getRotate() {
                return rotate;
            }

            public void setRotate(int rotate) {
                this.rotate = rotate;
            }

            public int getLinewidth() {
                return linewidth;
            }

            public void setLinewidth(int linewidth) {
                this.linewidth = linewidth;
            }

            public int getHeight() {
                return height;
            }

            public void setHeight(int height) {
                this.height = height;
            }
        }

        public class DrawQrCodeBean {
            /**
             * start_x : 404
             * start_y : 59
             * text : http://weixin.qq.com/r/czj27qvE8vaNre_N921h
             * rotate : 0
             * ver : 3
             * lel : 2
             */

            private int start_x;
            private int start_y;
            private String text;
            private int rotate;
            private int ver;
            private int lel;

            public int getStart_x() {
                return start_x;
            }

            public void setStart_x(int start_x) {
                this.start_x = start_x;
            }

            public int getStart_y() {
                return start_y;
            }

            public void setStart_y(int start_y) {
                this.start_y = start_y;
            }

            public String getText() {
                return text;
            }

            public void setText(String text) {
                this.text = text;
            }

            public int getRotate() {
                return rotate;
            }

            public void setRotate(int rotate) {
                this.rotate = rotate;
            }

            public int getVer() {
                return ver;
            }

            public void setVer(int ver) {
                this.ver = ver;
            }

            public int getLel() {
                return lel;
            }

            public void setLel(int lel) {
                this.lel = lel;
            }
        }
    }
}
