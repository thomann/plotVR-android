package io.github.thomann.plotvr;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by pht on 08.11.15.
 */
public class DownloadTask extends AsyncTask<String, Void, Data> {
    public static final String TAG = DownloadTask.class.getSimpleName();

    private PlotVRActivity plotVRActivity;

    public DownloadTask(PlotVRActivity dataVRActivity) {
        this.plotVRActivity = dataVRActivity;
    }

    @Override
    protected Data doInBackground(String... hosts) {
        try {
            return loadFromNetwork(hosts[0]);
        } catch (IOException e) {
            e.printStackTrace();
            return plotVRActivity.loadSampleData();
        }
    }

    /**
     * Override this method to perform a computation on a background thread. The
     * specified parameters are the parameters passed to {@link #execute}
     * by the caller of this task.
     * <p/>
     * This method can call {@link #publishProgress} to publish updates
     * on the UI thread.
     *
     * @param params The parameters of the task.
     * @return A result, defined by the subclass of this task.
     * @see #onPreExecute()
     * @see #onPostExecute
     * @see #publishProgress
     */

    /**
     * Uses the logging framework to display the output of the fetch
     * operation in the log fragment.
     */
    @Override
    protected void onPostExecute(Data result) {
        Log.i(PlotVRActivity.TAG, "Loaded json");
        plotVRActivity.setData(result);
//            try {
//                Log.i(TAG, "Loading "+result);
//                if(result != null)
//                    readJson(new StringReader(result));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
    }


    /** Initiates the fetch operation. */
    private Data loadFromNetwork(String urlString) throws IOException {
        InputStream stream = null;
        String str ="";

        try {
            Log.i(TAG,"Loading "+urlString);
            stream = downloadUrl(urlString);
            //str = readIt(stream, 100000);
            return new Data().readJson(new InputStreamReader(stream));
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    /**
     * Given a string representation of a URL, sets up a connection and gets
     * an input stream.
     * @param urlString A string representation of a URL.
     * @return An InputStream retrieved from a successful HttpURLConnection.
     * @throws java.io.IOException
     */
    private static InputStream downloadUrl(String urlString) throws IOException {
        // BEGIN_INCLUDE(get_inputstream)
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000 /* milliseconds */);
        conn.setConnectTimeout(15000 /* milliseconds */);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        // Start the query
        conn.connect();
        InputStream stream = conn.getInputStream();
        return stream;
        // END_INCLUDE(get_inputstream)
    }



}
