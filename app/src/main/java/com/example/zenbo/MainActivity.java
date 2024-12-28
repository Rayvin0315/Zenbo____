package com.example.zenbo;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotCallback;
import com.asus.robotframework.API.Utility;
import com.asus.robotframework.API.RobotFace;
import com.asus.robotframework.API.RobotCmdState;
import com.asus.robotframework.API.RobotErrorCode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends RobotActivity {
    public static MainActivity instance; // 静态实例
    private static final int REQUEST_CODE_SPEECH_INPUT = 1;
    private Handler handler = new Handler();
    private TextView txt;

    public static RobotCallback robotCallback = new RobotCallback() {
        private boolean hasWelcomed = false; // 标志位，防止重复欢迎语

        @Override
        public void onResult(int cmd, int serial, RobotErrorCode err_code, Bundle result) {
            super.onResult(cmd, serial, err_code, result);
        }

        @Override
        public void onStateChange(int cmd, int serial, RobotErrorCode err_code, RobotCmdState state) {
            super.onStateChange(cmd, serial, err_code, state);
        }

        @Override
        public void initComplete() {
            super.initComplete();
            if (!hasWelcomed) { // 检查是否已经说过欢迎语
                hasWelcomed = true;
                MainActivity.instance.robotAPI.robot.speak("歡迎光臨，請說出需要的商品名稱");
                MainActivity.instance.checkDatabaseConnection();
            }
        }
    };

    public static RobotCallback.Listen robotListenCallback = new RobotCallback.Listen() {
        @Override
        public void onFinishRegister() {}

        @Override
        public void onVoiceDetect(JSONObject jsonObject) {}

        @Override
        public void onSpeakComplete(String s, String s1) {}

        @Override
        public void onEventUserUtterance(JSONObject jsonObject) {
            try {
                String utterance = jsonObject.getString("text"); // 获取语音识别的文字结果
                Log.v("Voice Input", "User said: " + utterance);

                MainActivity.instance.fetchProductDetails(utterance);
            } catch (Exception e) {
                Log.e("VoiceRecognition", e.toString());
            }
        }

        @Override
        public void onResult(JSONObject jsonObject) {}

        @Override
        public void onRetry(JSONObject jsonObject) {}
    };

    public MainActivity() {
        super(robotCallback, robotListenCallback);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (instance == null) { // 确保只初始化一次
            instance = this;
        }

        txt = findViewById(R.id.txt);
        this.robotAPI = new RobotAPI(getApplicationContext(), robotCallback);

        startSpeechRecognition();
    }

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "請說出商品名稱");

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            speakOut("語音識別啟動失敗");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String spokenText = result.get(0);
                speakOut("您選擇的商品是 " + spokenText);
                fetchProductDetails(spokenText);
            }
        }
    }

    public void fetchProductDetails(String productName) {
        String url = "http://192.168.0.105/get_product.php?product_name=" + productName;

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    String errorMessage = "网络请求失败";
                    txt.setText(errorMessage);
                    speakOut(errorMessage);
                });
                Log.e("HTTP_ERROR", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();

                    runOnUiThread(() -> {
                        try {
                            JSONObject jsonObject = new JSONObject(responseData);

                            if (jsonObject.has("error")) {
                                String error = jsonObject.getString("error");
                                txt.setText(error);
                                speakOut(error);
                            } else {
                                // 获取数据
                                String name = jsonObject.getString("name");
                                double price = jsonObject.getDouble("price");
                                int stock = jsonObject.getInt("stock");
                                String location = jsonObject.getString("location");

                                // 格式化显示和语音内容
                                String productInfo = String.format(
                                        "商品名称：%s\n价格：%.2f\n库存：%d\n位置：%s",
                                        name, price, stock, location
                                );

                                // 在屏幕上显示
                                txt.setText(productInfo);

                                // 用语音播报，包括库存信息
                                String speechInfo = String.format(
                                        "商品名称：%s，价格是%.2f元，库存有%d件，位置在%s。请确认库存信息。",
                                        name, price, stock, location
                                );
                                speakOut(speechInfo);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            String parseError = "服务器响应解析失败";
                            txt.setText(parseError);
                            speakOut(parseError);
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        String serverError = "服务器错误，错误码：" + response.code();
                        txt.setText(serverError);
                        speakOut(serverError);
                    });
                }
            }
        });
    }

    private void checkDatabaseConnection() {
        String url = "http://192.168.0.105/get_product.php?product_name=test";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    speakOut("无法连接到数据库");
                    txt.setText("数据库连接失败");
                });
                Log.e("DB_CONNECTION", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        speakOut("数据库连接成功");
                        txt.setText("数据库连接成功");
                    });
                } else {
                    runOnUiThread(() -> {
                        speakOut("数据库连接失败，错误码：" + response.code());
                        txt.setText("数据库连接失败，错误码：" + response.code());
                    });
                }
            }
        });
    }

    private void speakOut(String text) {
        if (robotAPI != null) {
            robotAPI.robot.speak(text);
            Log.v("SpeakOut", "Zenbo speaking: " + text);
        } else {
            Log.e("SpeakOut", "RobotAPI is not initialized");
        }
    }

    @Override
    protected void onDestroy() {
        if (robotAPI != null) {
            robotAPI.release();
        }
        super.onDestroy();
    }
}
