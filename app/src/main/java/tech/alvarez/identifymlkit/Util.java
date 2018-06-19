package tech.alvarez.identifymlkit;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static tech.alvarez.identifymlkit.MainActivity.DIM_BATCH_SIZE;
import static tech.alvarez.identifymlkit.MainActivity.DIM_IMG_SIZE_X;
import static tech.alvarez.identifymlkit.MainActivity.DIM_IMG_SIZE_Y;
import static tech.alvarez.identifymlkit.MainActivity.DIM_PIXEL_SIZE;

public class Util {

  static final int[] intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

  public static List<String> loadLabelList(Activity activity) {
    List<String> labelList = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(activity.getAssets().open("labels.txt")))) {
      String line;
      while ((line = reader.readLine()) != null) {
        labelList.add(line);
      }
    } catch (IOException e) {
      Log.e("", "Failed to read label list.", e);
    }
    return labelList;
  }


  public static Bitmap getBitmapFromAsset(Context context, String filePath) {
    AssetManager assetManager = context.getAssets();
    InputStream is;
    Bitmap bitmap = null;
    try {
      is = assetManager.open(filePath);
      bitmap = BitmapFactory.decodeStream(is);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return bitmap;
  }

  public static synchronized ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap, int width, int height) {
    ByteBuffer imgData = ByteBuffer.allocateDirect(DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
    imgData.order(ByteOrder.nativeOrder());
    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, true);
    imgData.rewind();
    scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
    int pixel = 0;
    for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
      for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
        final int value = intValues[pixel++];
        imgData.put((byte) ((value >> 16) & 0xFF));
        imgData.put((byte) ((value >> 8) & 0xFF));
        imgData.put((byte) (value & 0xFF));
      }
    }
    return imgData;
  }
}
