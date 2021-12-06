# LinFolo
<img height="55" src="app/src/main/res/drawable/icon.png" align="left" style="margin: 0 10px" /> This app is part of the miniature Segway project at TGM (Vienna). This Android app enables the Segway to autonomously follow a line on the floor. The Android device's camera and OpenCV are used to detect the line. The line's x-coordinate is then encoded and sent to the Segway using the Android device's headphone jack and a special adapter. The signal sent can be very loud and is not meant to be listened to. Do not connect any speakers / headphones when using the app!

More info: https://electronics.fragner.org/linfolo

## Build
Download this repository and open it using Android Studio. Select your connected device and run the app.

## How to use
Connect the Segway using the audio-jack and launch the app. The buttons are enabled, as soon as a device is plugged into the audio-jack. Select the line by tapping it on the screen of the Android device. The buttons on the left and right allow to choose the Segway's direction when it encounters a junction. The top right button enables the Segway's lights.

## Attention
When updating OpenCV don't forget to modify `./openCVLibrary***/src/main/java/org/opencv/android/JavaCameraView.java` in order to get the Flashlight activated.

## Disclaimer
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.