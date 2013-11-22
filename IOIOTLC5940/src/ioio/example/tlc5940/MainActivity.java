package ioio.example.tlc5940;

import com.an_asumin.ioiotlc5940.R;

import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.GridLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends IOIOActivity {
	// Number of TLC5940!
	private final int tlcNum_ = 3;

	// For GUI
	private final int toggleButtonColNum_ = 5;	// you can change this for layout
	private SeekBar[] seekBarColors_;
	private GridLayout gridLayoutForToggleButton_;
	private ToggleButton[] toggleButtonLEDs_;
	private ToggleButton toggleButtonKnightRider_;
	private TextView textViewFrameCounter_;
	private TextView textViewLedOpenDetection_;
	private TextView textViewThermalErrorFlag_;
	
	// GUI updating
	private Handler handler_;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// for dynamic GUI update
		handler_ = new Handler();
		
		// seek bar - RGB brightness
		seekBarColors_ = new SeekBar[3];
		seekBarColors_[0] = (SeekBar) findViewById(R.id.seekBarRed);
		seekBarColors_[1] = (SeekBar) findViewById(R.id.seekBarGreen);
		seekBarColors_[2] = (SeekBar) findViewById(R.id.seekBarBlue);
		for(int i = 0; i < 3; ++i){
			seekBarColors_[i].setMax(IOIOTLC5940.GRAYSCALE_STEP_NUM - 1);
			seekBarColors_[i].setProgress(0);
		}
		
		// grid layout - LED control
		gridLayoutForToggleButton_ = (GridLayout) findViewById(R.id.gridLayoutForToggleButtons);
		toggleButtonLEDs_ = new ToggleButton[tlcNum_ / 3 * IOIOTLC5940.TLC_CHANNEL_NUM];
		for(int i = 0; i < toggleButtonLEDs_.length; ++i){
			toggleButtonLEDs_[i] = new ToggleButton(this);
			toggleButtonLEDs_[i].setChecked(true);
			CharSequence text = "LED "+ Integer.toString(i);
			toggleButtonLEDs_[i].setText(text);
			toggleButtonLEDs_[i].setTextOn(text);
			toggleButtonLEDs_[i].setTextOff(text);
			GridLayout.LayoutParams params = new GridLayout.LayoutParams();
			params.rowSpec = GridLayout.spec(i / toggleButtonColNum_);
			params.columnSpec = GridLayout.spec(i % toggleButtonColNum_);
			toggleButtonLEDs_[i].setLayoutParams(params);
			gridLayoutForToggleButton_.addView(toggleButtonLEDs_[i]);
		}
		
		// toggle button - Knight Rider
		toggleButtonKnightRider_ = (ToggleButton) findViewById(R.id.toggleButtonKnightRider);
		toggleButtonKnightRider_.setChecked(false);
		toggleButtonKnightRider_.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			// Knight Rider mode is exclusive
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(isChecked){
					// disable seek bars
					for(int i = 0; i < seekBarColors_.length; ++i){
						seekBarColors_[i].setEnabled(false);
					}
					// disable toggle buttons
					for(int i = 0; i < toggleButtonLEDs_.length; ++i){
						toggleButtonLEDs_[i].setEnabled(false);
					}
				}else{
					// enable seek bars
					for(int i = 0; i < seekBarColors_.length; ++i){
						seekBarColors_[i].setEnabled(true);
					}
					// enable toggle buttons
					for(int i = 0; i < toggleButtonLEDs_.length; ++i){
						toggleButtonLEDs_[i].setEnabled(true);
					}					
				}
			}
		});
		
		// some text view...
		textViewFrameCounter_ = (TextView) findViewById(R.id.textViewFrameCounter);
		textViewLedOpenDetection_ = (TextView) findViewById(R.id.textViewLedOpenDetection);
		textViewThermalErrorFlag_ = (TextView) findViewById(R.id.textViewThermalErrorFlag);
	}

	class Looper extends BaseIOIOLooper {
		// TLC5940
		private IOIOTLC5940 tlc_;

		// Knight Rider
		private int knightRiderSpotLedNum_ = 0;
		private int knightRiderDirection_ = 1;
		// frame counter
		private long baseTime;
		private long now;
		private int frameCounter;
		private float frameRate;
		
		
		@Override
		protected void setup() throws ConnectionLostException, InterruptedException {
			
			// TLC5940 setting
			tlc_ = new IOIOTLC5940(ioio_, tlcNum_, IOIOPinsList.VPRG_PIN_NUM, IOIOPinsList.SIN_PIN_NUM, IOIOPinsList.SCLK_PIN_NUM, IOIOPinsList.XLAT_PIN_NUM, IOIOPinsList.BLANK_PIN_NUM, IOIOPinsList.GSCLK_PIN_NUM, IOIOPinsList.SOUT_PIN_NUM, IOIOPinsList.SLAVE_SELECT_PIN_NUM, true);
			tlc_.setAllDotCorrection(1.0f);
			tlc_.updateDotCorrection();			
			for(int i = 0; i < tlcNum_ * IOIOTLC5940.TLC_CHANNEL_NUM; ++i){
				if(!toggleButtonLEDs_[i / 3].isChecked()) tlc_.setGrayscale(i, 0);
				tlc_.setGrayscaleInStep(i, seekBarColors_[i % 3].getProgress());
			}
			tlc_.updateGrayscale();
			tlc_.setEnabled(true);
			
			// frame counter
			frameCounter = 0;
			baseTime = System.currentTimeMillis();
		}
		
		@Override
		public void loop() throws ConnectionLostException, InterruptedException {
			
			// Knight Rider mode!
			if(toggleButtonKnightRider_.isChecked()){
				
				// turn on the LED(only red channel) that become the focus
				for(int i = 0; i < tlcNum_ * IOIOTLC5940.TLC_CHANNEL_NUM; ++i) tlc_.setGrayscale(i, 0.0f);
				tlc_.setGrayscale(knightRiderSpotLedNum_ * 3, 1.0f);
				// turn on next LED(only red channel, mid brightness) if it's possible,
				// and decide the direction in which light moves
				if(knightRiderSpotLedNum_ != 0){
					tlc_.setGrayscale((knightRiderSpotLedNum_ - 1) * 3, 0.5f);
				}else{
					knightRiderDirection_ = 1;
				}
				if(knightRiderSpotLedNum_ != tlcNum_ / 3 * IOIOTLC5940.TLC_CHANNEL_NUM){
					tlc_.setGrayscale((knightRiderSpotLedNum_ + 1) * 3, 0.5f);
				}else{
					knightRiderDirection_ = -1;
				}
				knightRiderSpotLedNum_ += knightRiderDirection_;
				tlc_.updateGrayscale();

			// Normal mode
			}else{
				// turn on the each LED according to the state of toggle buttons
				for(int i = 0; i < tlcNum_ * IOIOTLC5940.TLC_CHANNEL_NUM; ++i){
					if(toggleButtonLEDs_[i / 3].isChecked()) tlc_.setGrayscaleInStep(i, seekBarColors_[i % 3].getProgress());
				}

				if(tlc_.hasNewGrayscaleData()){
					tlc_.updateGrayscale();					
				}else{
					// nothing to update...
					Thread.sleep(10);
				}
			}

			// frame counter
			now = System.currentTimeMillis();
			++frameCounter;
			
			// GUI update is 1time/sec...
			if(now - baseTime > 1000){
				// LED Open Detection
				boolean openDetectionFlag = false;
				final StringBuilder ledOpenDetectionText = new StringBuilder();
				ledOpenDetectionText.append("Open channel: ");
				for(int i = 0; i < tlcNum_ * IOIOTLC5940.TLC_CHANNEL_NUM; ++i){
					if(tlc_.getLedOpenDetection(i)){
						ledOpenDetectionText.append(Integer.toString(i) + " ");
						openDetectionFlag = true;
					}
				}
				if(!openDetectionFlag) ledOpenDetectionText.append("none");
				// direct operation of UI is not allowed, ask the handler to change text view content.
				handler_.post(new Runnable() {
					@Override
					public void run() {					
						textViewLedOpenDetection_.setText(ledOpenDetectionText.toString());
					}
				});
			
				// Thermal Error flag
				boolean thermalErrorFlag = false;
				final StringBuilder thermalErrorFlagText = new StringBuilder();
				thermalErrorFlagText.append("Thermal Error TLC5940: ");
				for(int i = 0; i < tlcNum_; ++i){
					if(tlc_.getThermalErrorFlag(i)){
						thermalErrorFlagText.append(Integer.toString(i) + " ");
						thermalErrorFlag = true;
					}
				}
				if(!thermalErrorFlag) thermalErrorFlagText.append("none");
				handler_.post(new Runnable() {				
					@Override
					public void run() {
						textViewThermalErrorFlag_.setText(thermalErrorFlagText.toString());					
					}
				});
			
				// frame counter
				frameRate = (float) frameCounter * 1000 / (now - baseTime);
				handler_.post(new Runnable(){
					@Override
					public void run() {
						textViewFrameCounter_.setText("Frame Rate: " + Float.toString(frameRate) + " frames/sec");						
					}
				});
				baseTime = now;
				frameCounter = 0;
			}
		}
	}
	
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}	
}
