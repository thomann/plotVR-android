package io.github.thomann.plotvr;

import android.util.JsonReader;
import android.util.Log;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pht on 08.11.15.
 */
public class Data {

    private float[][] theData = null;
    private double speed;

    public Data() {

    }

    public Data readJson(Reader in) throws IOException {
        JsonReader reader = new JsonReader(in);
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("data")) {
                List<float[]> dataList = new ArrayList<float[]>();
                reader.beginArray();
                while (reader.hasNext()) {
                    float[] sample = new float[4];
                    reader.beginArray();
                    int i = 0;
                    while (reader.hasNext() && i < sample.length) {
                        sample[i++] = (float) reader.nextDouble();
                    }
                    reader.endArray();
                    dataList.add(sample);
                }
                reader.endArray();
                setTheData(dataList.toArray(new float[0][4]));
                Log.i(PlotVRActivity.TAG, dataList.toString());
            } else if (name.equals("speed")) {
                setSpeed(reader.nextDouble());
            } else
                reader.skipValue();
        }
        reader.endObject();
        return this;
    }

    public float[][] getTheData() {
        return theData;
    }

    public void setTheData(float[][] theData) {
        this.theData = theData;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    @Override
    public String toString() {
        return "{ Data: "+((theData==null)?"null":"samples: "+theData.length)+", speed="+speed + " }";
    }
}
