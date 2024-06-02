package com.tappgrade.androidcamera;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
//import androidx.camera.video.VideoCapture;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer {

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    PreviewView previewView;
    private ImageCapture imageCapture;
    private VideoCapture videoCapture;
    private Button bRecord;
    private Button bCapture;
    private Button recButton;
    private Button imgUploadButton;

    //Used for Looping video recording
    private Handler handlerVideoLoop = new Handler(Looper.getMainLooper());
    private Runnable runnableVideoLoop;
    private static final int START_RECORD_INTERVAL = 2000; // 2 seconds
    private static final int RECORD_DURATION_INTERVAL = 10000; // 10 seconds
    private Boolean isRecording = false;

    //Uncomment is the permission code from the bottom of the page is used
    //private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        bCapture = findViewById(R.id.bCapture);
        bRecord = findViewById(R.id.bRecord);
        bRecord.setText("start recording"); // Set the initial text of the button

        recButton = findViewById(R.id.recButton);
        imgUploadButton = findViewById(R.id.imgUploadButton);

        //If capture image button is clicked
        bCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                capturePhoto();
            }
        });

        //If the record button is clicked
        bRecord.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onClick(View v) {
                if (bRecord.getText() == "start recording"){
                    bRecord.setText("stop recording");
                    recordVideo();
                } else {
                    bRecord.setText("start recording");
                    videoCapture.stopRecording();
                }
            }
        });

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecutor());

        //Start recording in loop
        recordInLoop();
    }

    Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @SuppressLint("RestrictedApi")
    private void startCameraX(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        Preview preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image capture use case
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // Video capture use case
        videoCapture = new VideoCapture.Builder()
                .setVideoFrameRate(30)
                .setBitRate(4000000)
                .build();

        // Image analysis use case
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(getExecutor(), this);

        //bind to lifecycle:
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture, videoCapture);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        // image processing here for the current frame
        Log.d("TAG", "analyze: got the frame at: " + image.getImageInfo().getTimestamp());
        image.close();
    }


    @SuppressLint("RestrictedApi")
    private void recordVideo() {
        if (videoCapture != null) {

            long timestamp = System.currentTimeMillis();
            ContentValues contentValues = new ContentValues();
            Uri outputUri;

            // For Android 10 (API 29) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Construct the relative path for the folder
                String relativePath = Environment.DIRECTORY_DCIM + "/CameraX/CameraXVideo";

                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);

                outputUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);

            } else {
                // For Android 9 (API 28) and below
                File movieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                File videoFolder = new File(movieFile, "CameraX/CameraXVideo");
                if (!videoFolder.exists()) {
                    videoFolder.mkdirs();
                }

                File videoFile = new File(videoFolder, timestamp + ".mp4");
                outputUri = Uri.fromFile(videoFile);
            }

            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                VideoCapture.OutputFileOptions outputFileOptions;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    outputFileOptions = new VideoCapture.OutputFileOptions.Builder(
                            getContentResolver(),
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                    ).build();
                } else {
                    outputFileOptions = new VideoCapture.OutputFileOptions.Builder(
                            new File(outputUri.getPath())
                    ).build();
                }

                isRecording = true;
                videoCapture.startRecording(
                        outputFileOptions,
                        getExecutor(),
                        new VideoCapture.OnVideoSavedCallback() {
                            @Override
                            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                                isRecording = false;
                                Toast.makeText(MainActivity.this, "Video has been saved successfully.", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                                isRecording = false;
                                Toast.makeText(MainActivity.this, "Error saving video: " + message, Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private void capturePhoto() {
        long timestamp = System.currentTimeMillis();
        ContentValues contentValues = new ContentValues();
        Uri outputUri;

        // For Android 10 (API 29) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relativePath = Environment.DIRECTORY_DCIM + "/CameraX/CameraXImages";
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        } else {
            // For Android 9 (API 28) and below
            File pictureFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File imageFolder = new File(pictureFile, "CameraX/CameraXImages");
            if (!imageFolder.exists()) {
                imageFolder.mkdirs();
            }

            File imageFile = new File(imageFolder, timestamp + ".jpg");
            outputUri = Uri.fromFile(imageFile);
        }

        try {
            ImageCapture.OutputFileOptions outputFileOptions;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                outputFileOptions = new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build();
            } else {
                outputFileOptions = new ImageCapture.OutputFileOptions.Builder(
                        new File(outputUri.getPath())
                ).build();
            }

            imageCapture.takePicture(
                    outputFileOptions,
                    getExecutor(),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Toast.makeText(MainActivity.this, "Photo has been saved successfully.", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Toast.makeText(MainActivity.this, "Error saving photo: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("RestrictedApi")
    public void recordInLoop() {
        runnableVideoLoop = new Runnable() {
            @Override
            public void run() {
                if(!isRecording) {
                    recordVideo();
                    recButton.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, "Recording Started", Toast.LENGTH_SHORT).show();

                    //This tells the duration of the video.
                    // When it triggers the record will be stopped and the next handler will be triggered
                    handlerVideoLoop.postDelayed(this, RECORD_DURATION_INTERVAL);

                } else {
                    videoCapture.stopRecording();
                    recButton.setVisibility(View.INVISIBLE);

                    //This will start a new video recording
                    handlerVideoLoop.postDelayed(this, START_RECORD_INTERVAL);
                }
            }
        };
        //First start (Initialisation) of the Runnable Anonymous Class to record in loop
        handlerVideoLoop.postDelayed(runnableVideoLoop, START_RECORD_INTERVAL);
    }





    @Override
    protected void onDestroy() {
        super.onDestroy();
        handlerVideoLoop.removeCallbacks(runnableVideoLoop);
    }




}