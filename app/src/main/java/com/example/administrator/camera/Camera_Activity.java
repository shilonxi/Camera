package com.example.administrator.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.support.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 物联网特性（无屏幕）致使本程序无预览功能，可使用自带预览
 依据条件（此处简化为按钮）抓拍，显示及保存
 这里的图片以时间命名，未来可作识别功能
 */

public class Camera_Activity extends Activity
{
    private static final SparseIntArray ORIENTATIONS=new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    //方向旋转
    private ImageView imageView;
    private Button button;
    //定义变量
    private HandlerThread handlerThread;
    private Handler childHandler;
    //线程与子线程
    private ImageReader mImageReader;
    private Bitmap bitmap;
    //处理相片
    private CameraDevice mCameraDevice;
    private CameraManager mCameraManager;
    private String mCameraID;
    //第一个摄像头ID为0
    //摄像相关
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Window w=getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        //硬加速
        setContentView(R.layout.camera_layout);
        imageView=(ImageView)findViewById(R.id.imageView);
        button=(Button)findViewById(R.id.button);
        //获取实例
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
                //开启相机抓拍
            }
        });
        //按钮监听
    }

    private void openCamera()
    {
        handlerThread=new HandlerThread("Camera2");
        handlerThread.start();
        childHandler=new Handler(handlerThread.getLooper());
        //异步处理
        mImageReader=ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
        //相片设置
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener()
        {
            @Override
            public void onImageAvailable(ImageReader reader)
            {
                mCameraDevice.close();
                mCameraDevice=null;
                //关闭摄像头
                Image image=reader.acquireLatestImage();
                //获取预览图像
                ByteBuffer buffer=image.getPlanes()[0].getBuffer();
                byte[] bytes=new byte[buffer.remaining()];
                buffer.get(bytes);
                //由缓冲区存入字节数组
                bitmap=BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                bitmap=Bitmap.createScaledBitmap(bitmap, 640, 480, true);
                //生成图片待显示
                try
                {
                    if (bitmap!=null)
                    {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run()
                            {
                                imageView.setImageBitmap(bitmap);
                                //显示
                            }
                        });
                        SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyyMMddHHmmss");
                        //yyyy年MM月dd日 HH:mm:ss
                        Date date=new Date(System.currentTimeMillis());
                        String name=simpleDateFormat.format(date);
                        //以时间命名文件名
                        File file=new File("/sdcard/"+name+".jpg");
                        //存储地址
                        BufferedOutputStream bufferedOutputStream=new BufferedOutputStream(new FileOutputStream(file));
                        bitmap.compress(Bitmap.CompressFormat.JPEG,100,bufferedOutputStream);
                        bufferedOutputStream.flush();
                        bufferedOutputStream.close();
                        if(file.exists())
                            Toast.makeText(Camera_Activity.this,"保存成功",Toast.LENGTH_SHORT).show();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }, childHandler);

        if (mCameraManager==null)
            mCameraManager=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
        //获取服务
        String cameraIds[]={};
        try {
            cameraIds=mCameraManager.getCameraIdList();
            //获取摄像头列表
        } catch (CameraAccessException e) {
            Log.e("camera", "ID exception", e);
        }
        if (cameraIds.length<1) {
            Log.e("camera", "No cameras found");
            return;
        }
        mCameraID=cameraIds[0];
        //锁定CSI摄像头
        try {
            mCameraManager.openCamera(mCameraID, stateCallback, null);
            //打开摄像头
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraDevice.StateCallback stateCallback=new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            //打开摄像头
            takePicture();
            //拍照
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            if (null!=mCameraDevice)
            {
                mCameraDevice.close();
                mImageReader.close();
            }
            mCameraDevice=null;
            //关闭摄像头
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraDevice=null;
            Toast.makeText(Camera_Activity.this,"开启失败",Toast.LENGTH_SHORT).show();
            //报错
        }
    };
    //回调监听

    private void takePicture()
    {
        try
        {
            final CaptureRequest.Builder builder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //创建适用于静态图像捕获的请求
            builder.addTarget(mImageReader.getSurface());
            int rotation=getWindowManager().getDefaultDisplay().getRotation();
            builder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));
            //根据设备方向计算设置照片的方向
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (mCameraDevice==null)
                        return;
                    try {
                        CaptureRequest mCaptureRequest=builder.build();
                        session.capture(mCaptureRequest, null, childHandler);
                        //捕获静态图像
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(Camera_Activity.this,"配置错误",Toast.LENGTH_SHORT).show();
                }
            },childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
