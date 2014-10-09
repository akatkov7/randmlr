package me.andriykatkov.randmlr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class Randmlr extends Activity {
	private ImageView imageView;
	private EditText tumblrNameField;
	private String tumblrName;
	private boolean loading = false;

	private Toast ENTER_NAME, CHECK_NAME, BAD_CONNECTION, UNKNOWN_ERROR;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.randmlr_view);

		ENTER_NAME = Toast.makeText(this, "Please enter a tumblr name!",
				Toast.LENGTH_SHORT);
		CHECK_NAME = Toast.makeText(this,
				"Check to make sure your tumblr name is correct.",
				Toast.LENGTH_SHORT);
		BAD_CONNECTION = Toast
				.makeText(
						this,
						"There was an error connecting to Tumblr! Please try again later.",
						Toast.LENGTH_SHORT);
		UNKNOWN_ERROR = Toast.makeText(this,
				"An unknown error occured. Please try again later.",
				Toast.LENGTH_SHORT);

		imageView = (ImageView) findViewById(R.id.tumblrImage);
		imageView.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (!loading) {
					if (tumblrName != null && !tumblrName.isEmpty()) {
						loadImage();
					} else {
						ENTER_NAME.show();
					}
				}
				return true;
			}
		});
		tumblrNameField = (EditText) findViewById(R.id.tumblrName);
		tumblrNameField.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_GO) {
					setTumblrName(v);
					handled = true;
				}
				return handled;
			}
		});
	}

	public void setTumblrName(View v) {
		tumblrName = "http://" + tumblrNameField.getText().toString()
				+ ".tumblr.com/random";
		loadImage();
	}

	private void loadImage() {
		new MyTask().execute(tumblrName);
	}

	private class MyTask extends AsyncTask<String, Void, Bitmap> {

		private boolean invalidTumblr = false;
		private boolean connectionError = false;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			loading = true;
		}

		@Override
		protected void onPostExecute(Bitmap b) {
			super.onPostExecute(b);
			if (b != null) {
				imageView.setImageBitmap(b);
			} else {
				if (invalidTumblr) {
					CHECK_NAME.show();
				}
				if (connectionError) {
					BAD_CONNECTION.show();
				}
				if (!invalidTumblr && !connectionError) {
					UNKNOWN_ERROR.show();
				}
			}
			loading = false;
		}

		public String getRandomPostURL(String url) {
			// make a request to Tumblr's random feature
			URL request = null;
			try {
				request = new URL(url);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				invalidTumblr = true;
			}
			if (request != null) {
				HttpURLConnection random = null;
				try {
					random = (HttpURLConnection) request.openConnection();
				} catch (IOException e) {
					e.printStackTrace();
					connectionError = true;
				}
				if (random != null) {
					random.setRequestProperty(
							"User-Agent",
							"Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.107 Safari/537.36");
					random.setInstanceFollowRedirects(false);
					// the /random request returns the location of the random
					// post
					return random.getHeaderField("Location");
				}
			}
			return null;
		}

		public String getResponseFromPotentialImage(String location) {
			String maybeImage = location.replace("post", "image");
			maybeImage = maybeImage.substring(0, maybeImage.length() - 4);
			URL maybeImageURL = null;
			try {
				maybeImageURL = new URL(maybeImage);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
			if (maybeImageURL != null) {
				HttpURLConnection conn = null;
				try {
					conn = (HttpURLConnection) maybeImageURL.openConnection();
				} catch (IOException e) {
					e.printStackTrace();
					connectionError = true;
				}
				if (conn != null) {
					int responseCode = 0;
					try {
						responseCode = conn.getResponseCode();
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (responseCode == 200) {
						StringBuilder sb = null;
						try {
							BufferedReader br = new BufferedReader(
									new InputStreamReader(conn.getInputStream()));
							sb = new StringBuilder();
							String line;
							while ((line = br.readLine()) != null) {
								sb.append(line + "\n");
							}
							br.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						if (sb != null) {
							return sb.toString();
						}
					}
				}
			}
			return null;
		}

		@Override
		protected Bitmap doInBackground(String... urls) {
			String url = urls[0];
			// make a request to Tumblr's random feature
			String location = getRandomPostURL(url);
			if (location != null) {
				// we attempt to replace the word post with image to crudely
				// check if the post is an image, if it is, then the url
				// will return a 200 and we know it's a random image
				String response = getResponseFromPotentialImage(location);
				if (response != null) {
					int index = response.indexOf("data-src=\"");
					if (index >= 0) {
						// the index + the length of "data-src=""
						response = response.substring(index + 10);
						response = response.substring(0,
								response.indexOf("\" "));
						Bitmap bitmap = null;
						try {
							HttpURLConnection imageLink = (HttpURLConnection) (new URL(
									response).openConnection());
							bitmap = BitmapFactory.decodeStream(imageLink
									.getInputStream());
						} catch (IOException e) {
							e.printStackTrace();
							connectionError = true;
						}
						if (bitmap != null) {
							return bitmap;
						}
					} else {
						invalidTumblr = true;
						return null;
					}
				} else {
					// this wasn't an image so try again
					loadImage();
					return null;
				}
			}
			// this was not a valid tumblr so don't try again
			// just return null and show the error
			return null;
		}
	}

}
