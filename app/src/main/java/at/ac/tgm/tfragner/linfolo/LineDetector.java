package at.ac.tgm.tfragner.linfolo;

/*****************************
 * Theodor Fragner	         *
 * TGM Wien 1200             *
 * 1BHEL 2016/2017	         *
 * Segway-Controller         *
 * Klasse erkennt die zu     *
 * verfolgende Linie         *
 *****************************/

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class LineDetector {
    private Scalar mLowerBound = new Scalar(0),             // lower and upper bounds for range checking in HSV color space
                   mUpperBound = new Scalar(0);             //

    private static double mMinContourArea = 0.1;               // minimum contour area in percent for contours filtering

    private Scalar mColorRadius        = new Scalar(25,50,50,0);   // color radius for range checking in HSV color space
    private Mat mSpectrum              = new Mat();
    private List<MatOfPoint> mContours = new ArrayList<>();

    private Mat mPyrDownMat  = new Mat(),                      // cache
                mHsvMat      = new Mat(),                      //
                mMask        = new Mat(),                      //
                mDilatedMask = new Mat(),                      //
                mHierarchy   = new Mat();                      //

    public void setHsvColor(Scalar hsvColor) {
        double minV = (hsvColor.val[2] >= mColorRadius.val[2]) ? hsvColor.val[2] - mColorRadius.val[2] : 0;
        double maxV = (hsvColor.val[2] + mColorRadius.val[2] <= 255) ? hsvColor.val[2] + mColorRadius.val[2] : 255;

        mLowerBound.val[0] = 0;
        mUpperBound.val[0] = 255;

        mLowerBound.val[1] = hsvColor.val[1] - mColorRadius.val[1];
        mUpperBound.val[1] = hsvColor.val[1] + mColorRadius.val[1];

        mLowerBound.val[2] = minV;
        mUpperBound.val[2] = maxV;

        mLowerBound.val[3] = 0;
        mUpperBound.val[3] = 255;

        Mat spectrumHsv = new Mat(1, (int) (maxV - minV), CvType.CV_8UC3);

        //prepare data for spectrumLabel
        for (int j = 0; j < maxV - minV; j++) {
            byte[] tmp = {(byte) 0, (byte) 0, (byte) (minV + j)};
            spectrumHsv.put(0, j, tmp);
        }

        Imgproc.cvtColor(spectrumHsv, mSpectrum, Imgproc.COLOR_HSV2RGB_FULL, 4);
    }

    public Mat getSpectrum() {
        return mSpectrum;
    }

    public void process(Mat rgbaImage) {
        Imgproc.pyrDown(rgbaImage, mPyrDownMat);
        Imgproc.pyrDown(mPyrDownMat, mPyrDownMat);

        Imgproc.cvtColor(mPyrDownMat, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask);
        Imgproc.dilate(mMask, mDilatedMask, new Mat());

        List<MatOfPoint> contours = new ArrayList<>();

        Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // find max contour area
        double maxArea = 0;
        Iterator<MatOfPoint> each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea)
                maxArea = area;
        }

        // filter contours by area and resize to fit the original image size
        mContours.clear();
        each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint contour = each.next();
            if (Imgproc.contourArea(contour) > mMinContourArea * maxArea) {
                Core.multiply(contour, new Scalar(4, 4), contour);
                mContours.add(contour);
            }
        }
    }

    public List<MatOfPoint> getContours() {
        return mContours;
    }
}
