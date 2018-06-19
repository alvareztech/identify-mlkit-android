package tech.alvarez.identifymlkit;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseModelOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;
import com.google.firebase.ml.custom.model.FirebaseCloudModelSource;
import com.google.firebase.ml.custom.model.FirebaseLocalModelSource;
import com.google.firebase.ml.custom.model.FirebaseModelDownloadConditions;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class MainActivity extends AppCompatActivity {

  private ImageView photoImageView;
  private TextView labelsTextView;

  private Bitmap selectedBitmap;

  private static final int RESULTS_TO_SHOW = 3;

  static final int DIM_BATCH_SIZE = 1;
  static final int DIM_PIXEL_SIZE = 3;
  static final int DIM_IMG_SIZE_X = 224;
  static final int DIM_IMG_SIZE_Y = 224;

  private List<String> labelList;
  private final PriorityQueue<Map.Entry<String, Float>> sortedLabels =
    new PriorityQueue<>(
      RESULTS_TO_SHOW,
      new Comparator<Map.Entry<String, Float>>() {
        @Override
        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float>
          o2) {
          return (o1.getValue()).compareTo(o2.getValue());
        }
      });


  private FirebaseModelInterpreter interpreter;
  private FirebaseModelInputOutputOptions dataOptions;

  private static final String MODEL_NAME = "mymodelhosted";
  private static final String LOCAL_MODEL_NAME = "mymodel.tflite";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    photoImageView = findViewById(R.id.photoImageView);
    labelsTextView = findViewById(R.id.labelsTextView);

    labelList = Util.loadLabelList(this);

    findViewById(R.id.identifyButton).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        runModelInference();
      }
    });
    findViewById(R.id.onePhotoButton).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        changePhotoOne();
      }
    });
    findViewById(R.id.twoPhotoButton).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        changePhotoTwo();
      }
    });

    int[] inputDims = {DIM_BATCH_SIZE, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, DIM_PIXEL_SIZE};
    int[] outputDims = {DIM_BATCH_SIZE, labelList.size()};
    try {
      dataOptions = new FirebaseModelInputOutputOptions.Builder()
        .setInputFormat(0, FirebaseModelDataType.BYTE, inputDims)
        .setOutputFormat(0, FirebaseModelDataType.BYTE, outputDims)
        .build();
      FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions
        .Builder()
        .requireWifi()
        .build();

      FirebaseLocalModelSource localModelSource = new FirebaseLocalModelSource.Builder("asset")
        .setAssetFilePath(LOCAL_MODEL_NAME).build();

      FirebaseCloudModelSource cloudSource = new FirebaseCloudModelSource.Builder(MODEL_NAME)
        .enableModelUpdates(true)
        .setInitialDownloadConditions(conditions)
        .setUpdatesDownloadConditions(conditions)
        .build();

      FirebaseModelManager manager = FirebaseModelManager.getInstance();
      manager.registerLocalModelSource(localModelSource);
      manager.registerCloudModelSource(cloudSource);

      FirebaseModelOptions modelOptions = new FirebaseModelOptions.Builder()
        .setCloudModelName(MODEL_NAME)
        .setLocalModelName("asset")
        .build();

      interpreter = FirebaseModelInterpreter.getInstance(modelOptions);

    } catch (FirebaseMLException e) {
      showToast("Error while setting up the model");
      e.printStackTrace();
    }
  }

  private void changePhotoOne() {
    labelsTextView.setText(null);
    setImage("rusia.jpg");
  }

  private void changePhotoTwo() {
    labelsTextView.setText(null);
    setImage("accione2.jpg");
  }

  private void setImage(String file) {
    selectedBitmap = Util.getBitmapFromAsset(this, file);
    if (selectedBitmap != null) {
      Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();
      int targetWidth = targetedSize.first;
      int maxHeight = targetedSize.second;
      float scaleFactor = Math.max((float) selectedBitmap.getWidth() / (float) targetWidth, (float) selectedBitmap.getHeight() / (float) maxHeight);
      Bitmap resizedBitmap = Bitmap.createScaledBitmap(selectedBitmap, (int) (selectedBitmap.getWidth() / scaleFactor), (int) (selectedBitmap.getHeight() / scaleFactor), true);
      photoImageView.setImageBitmap(resizedBitmap);
      selectedBitmap = resizedBitmap;
    }
  }

  private void runModelInference() {
    if (interpreter == null) {
      Log.e("", "Image classifier has not been initialized; Skipped.");
      return;
    }
    ByteBuffer imgData = Util.convertBitmapToByteBuffer(selectedBitmap, selectedBitmap.getWidth(), selectedBitmap.getHeight());

    try {
      FirebaseModelInputs inputs = new FirebaseModelInputs.Builder().add(imgData).build();
      interpreter.run(inputs, dataOptions).addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
          e.printStackTrace();
          showToast("Error running model inference");
        }
      }).continueWith(
        new Continuation<FirebaseModelOutputs, List<String>>() {
          @Override
          public List<String> then(Task<FirebaseModelOutputs> task) {
            byte[][] labelProbArray = task.getResult().<byte[][]>getOutput(0);
            List<String> topLabels = getTopLabels(labelProbArray);

            labelsTextView.setText(topLabels.toString());

            return topLabels;
          }
        });
    } catch (FirebaseMLException e) {
      e.printStackTrace();
      showToast("Error running model inference");
    }

  }

  private synchronized List<String> getTopLabels(byte[][] labelProbArray) {
    for (int i = 0; i < labelList.size(); ++i) {
      sortedLabels.add(new AbstractMap.SimpleEntry<>(labelList.get(i), (labelProbArray[0][i] & 0xff) / 255.0f));
      if (sortedLabels.size() > RESULTS_TO_SHOW) {
        sortedLabels.poll();
      }
    }
    List<String> result = new ArrayList<>();
    final int size = sortedLabels.size();
    for (int i = 0; i < size; ++i) {
      Map.Entry<String, Float> label = sortedLabels.poll();
      result.add(label.getKey() + ":" + label.getValue());
    }
    Log.d("", "labels: " + result.toString());
    return result;
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
  }

  private Pair<Integer, Integer> getTargetedWidthHeight() {
    int targetWidth;
    int targetHeight;
    int maxWidthForPortraitMode = photoImageView.getWidth();
    int maxHeightForPortraitMode = photoImageView.getHeight();
    targetWidth = maxWidthForPortraitMode;
    targetHeight = maxHeightForPortraitMode;
    return new Pair<>(targetWidth, targetHeight);
  }
}
