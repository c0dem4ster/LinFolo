package at.ac.tgm.tfragner.linfolo;

/*****************************
 * Theodor Fragner	         *
 * TGM Wien 1200             *
 * 1BHEL 2016/2017	         *
 * Segway-Controller         *
 * App sucht eine Linie und  *
 * überträgt ihre Position   *
 * über die Audio-Buchse.    *
 *****************************/

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements Runnable, OnTouchListener, CvCameraViewListener2 {

    private Context              CONTEXT;                                                       // general

    private boolean              newData,                                                       // serial-communication
                                 lineEnd,                                                       //
                                 blinkerEnabled,                                                //
                                 where2Go,                  // true = left & false = right      //
                                 connected;                                                     //
    private final int            SAMPLE_RATE            = 16000,                                //
                                 BIT_DURATION           = 64,                                   //
                                 DURATION               = BIT_DURATION * 32;                    //
    private int                  toSend,                                                        //
                                 bufsizbytes            = DURATION * SAMPLE_RATE / 1000,        //
                                 bufsizsamps            = 110,                                  //
                                 txIntervall            = 50;                                   //
    private short[]              buffer                 = new short[bufsizsamps];               //
    private ToggleButton         blinkLeft,                                                     //
                                 blinkRight;                                                    //
    private ImageButton          toggleBlinker;                                                 //
    private Toast                dataToast;                                                     //
    private AudioManager         serialManager;                                                 //

    private int                  displayWidth;                                                  // line-detection
    private boolean              isColorSelected        = false;                                //
    private LineDetector         lineDetector;                                                  //
    private Mat                  mSpectrum,                                                     //
                                 mRgba;                                                         //
    private Size                 SPECTRUM_SIZE;                                                 //
    private Scalar               CONTOUR_COLOR_FOLLOW,                                          //
                                 CONTOUR_COLOR_IGNORE,                                          //
                                 mBlobColorRgba,                                                //
                                 mBlobColorHsv;                                                 //

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CONTEXT = getApplicationContext();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.line_detection_layout);
        Display display = getWindowManager().getDefaultDisplay();
        Point displaySize = new Point();
        display.getSize(displaySize);
        displayWidth = displaySize.x;

        blinkLeft = (ToggleButton) findViewById(R.id.blinkLeft);
        blinkLeft.setChecked(true);
        where2Go = true;
        blinkLeft.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                where2Go = isChecked;
                if (isChecked) {
                    blinkRight.setChecked(false);
                } else {
                    blinkRight.setChecked(true);
                }
            }
        });
        blinkRight = (ToggleButton) findViewById(R.id.blinkRight);
        blinkRight.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                where2Go = !isChecked;
                if (isChecked) {
                    blinkLeft.setChecked(false);
                } else {
                    blinkLeft.setChecked(true);
                }
            }
        });
        toggleBlinker = (ImageButton) findViewById(R.id.toggleBlinker);
        toggleBlinker.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                if (blinkerEnabled) {
                    blinkerEnabled = false;
                    toggleBlinker.setImageResource(R.drawable.blinker_disabled);
                } else {
                    blinkerEnabled = true;
                    toggleBlinker.setImageResource(R.drawable.blinker_enabled);
                }
            }
        });
        blinkerEnabled = true;

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        connected = false;
        enableControls(connected);
        initSerial();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    private void enableControls(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                blinkLeft.setEnabled(enable);
                blinkRight.setEnabled(enable);
                toggleBlinker.setEnabled(enable);
            }
        });
    }

    @Override
    public void run() {
        while (true) {
            if(serialManager.isWiredHeadsetOn()) {
                // look whether audio cable was connected since last time
                if (!connected) {
                    connected = true;
                    enableControls(connected);
                }
                if (newData) {
                    // the first two bits are reserved for line-end and blinker information
                    if (lineEnd) toSend += 0b10000000;
                    if (blinkerEnabled) toSend += 0b01000000;
                    // output the data to send on the display as well
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (dataToast == null) {
                                dataToast = Toast.makeText(CONTEXT, "dataToast initialized", Toast.LENGTH_LONG);
                            } else {
                                dataToast.setText(Integer.toBinaryString(toSend));
                            }
                            dataToast.show();
                        }
                    });
                    sendData();
                    newData = false;
                }
            } else if (connected){
                connected = false;
                enableControls(connected);
            }
        }
    }

    public void initSerial() {
        // needed for cable connection status
        serialManager = (AudioManager) CONTEXT.getSystemService(Context.AUDIO_SERVICE);
        Thread comm = new Thread(this, "comm");
        comm.start();
    }

    public void sendData() {
        int oneBit = 0x01;
        // initialization bits
        for (int i = 0; i < 10; i++) buffer[i] = (short) 0x0000;
        for (int i = 10; i < 20; i++) buffer[i] = (short) 0x7FFF;
        for (int i = 20; i < 30; i++) buffer[i] = (short) 0x0000;
        // data to be transmitted
        for (int currentBit = 0; currentBit < 8; currentBit++) {
            if (((toSend & (oneBit << currentBit)) >> currentBit) == 1) {
                for (int i = 100 - (currentBit * 10); i < 110 - (currentBit * 10); i++)
                    buffer[i] = (short) 0x7FFF;
            } else {
                for (int i = 100 - (currentBit * 10); i < 110 - (currentBit * 10); i++)
                    buffer[i] = (short) 0x0000;
            }
        }
        try {
            AudioTrack serial = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufsizbytes,
                    AudioTrack.MODE_STATIC);
            serial.setStereoVolume(1.0f, 1.0f);
            serial.write(buffer, 0, bufsizsamps);
            serial.play();
            Thread.sleep(txIntervall);
            serial.release();
            serial.reloadStaticData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        lineDetector = new LineDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        CONTOUR_COLOR_FOLLOW = new Scalar(0, 255, 0, 255);
        CONTOUR_COLOR_IGNORE = new Scalar(255, 0, 0, 255);
        SPECTRUM_SIZE = new Size(displayPercent(25), displayPercent(8));
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;
        int x = (int) event.getX() - xOffset;
        int y = (int) event.getY() - yOffset;

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x > 4) ? x - 4 : 0;
        touchedRect.y = (y > 4) ? y - 4 : 0;

        touchedRect.width = (x + 4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y + 4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width * touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++) {
            mBlobColorHsv.val[i] /= pointCount;
        }

        mBlobColorRgba = convertScalarHsv2Rgba(mBlobColorHsv);

        lineDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(lineDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        isColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        v.performClick();

        // don't need subsequent touch events
        return false;
    }

    public boolean contourTouchesTop(MatOfPoint contour) {
        org.opencv.core.Point[] points = contour.toArray();
        for (org.opencv.core.Point point: points) {
            if (point.y == 0) {
                return true;
            }
        }
        return false;
    }

    public List<MatOfPoint> filterContours(List<MatOfPoint> contours) {
        int contoursNotFiltered = contours.size();
        if (contours.isEmpty()) return contours;
        while (contoursNotFiltered > 0) {
            MatOfPoint contour = contours.get(contoursNotFiltered - 1);
            if (!contourTouchesTop(contour)) {
                contours.remove(contoursNotFiltered - 1);
            }
            contoursNotFiltered--;
        }
        return contours;
    }

    public int getXPosition(List<MatOfPoint> contours) {
        MatOfPoint contour = contours.get(0);
        org.opencv.core.Point[] points = contour.toArray();
        int sumXPositions = 0;
        int numXPositions = 0;
        for (org.opencv.core.Point point: points) {
            if (point.y == 0) {
                sumXPositions += point.x;
                numXPositions++;
            }
        }
        if (numXPositions != 0) {
            return sumXPositions / numXPositions;
        } else {
            return 0;
        }
    }

    public int displayPercent(int percent) {
        float percentAsDecimal = (float) percent / 100;
        return Math.round(displayWidth * percentAsDecimal);
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        toSend = 0;
        if (isColorSelected) {
            lineDetector.process(mRgba);
            List<MatOfPoint> rawContours = lineDetector.getContours();
            List<MatOfPoint> filteredContours = filterContours(rawContours);
            List<MatOfPoint> followContour = new ArrayList<>();
            if (!filteredContours.isEmpty()) {
                if (where2Go) {
                    followContour.add(filteredContours.get(filteredContours.size() - 1));
                    filteredContours.remove(rawContours.size() - 1);
                } else {
                    followContour.add(filteredContours.get(0));
                    filteredContours.remove(0);
                }
                toSend = ((getXPosition(followContour) * 63) / displayWidth);
                Imgproc.drawContours(mRgba, filteredContours, -1, CONTOUR_COLOR_IGNORE);
                Imgproc.drawContours(mRgba, followContour, -1, CONTOUR_COLOR_FOLLOW);
                lineEnd = false;
            } else {
                lineEnd = true;
            }
            newData = true;

            Mat colorLabel = mRgba.submat(displayPercent(1), displayPercent(9), displayPercent(1), displayPercent(9));
            colorLabel.setTo(mBlobColorRgba);

            Mat spectrumLabel = mRgba.submat(displayPercent(1), displayPercent(1) + mSpectrum.rows(), displayPercent(10), displayPercent(10) + mSpectrum.cols());
            mSpectrum.copyTo(spectrumLabel);
        }

        return mRgba;
    }

    private Scalar convertScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
}