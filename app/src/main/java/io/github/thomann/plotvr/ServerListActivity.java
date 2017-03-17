package io.github.thomann.plotvr;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class ServerListActivity extends AppCompatActivity {

    public static final String SAMPLE_DATA="Sample Data";
    
    private ArrayAdapter<String> serversAdapter;
    private List<String> serversArray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fabAdd = (FloatingActionButton) findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                serverDialog(-1);
            }
        });
        FloatingActionButton fabQR = (FloatingActionButton) findViewById(R.id.fabQR);
        fabQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startQRscanner();
            }
        });

        final ListView serverList = (ListView) findViewById(R.id.serverList);

        serversArray = new ArrayList<String>();

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        serversArray.addAll(sharedPref.getStringSet("servers.list", Collections.EMPTY_SET));
        if(!serversArray.contains(SAMPLE_DATA))
            serversArray.add(0, SAMPLE_DATA);

        serversAdapter = new ArrayAdapter<String>(
                this,
                R.layout.server_list_item,
                serversArray);

        serverList.setAdapter(serversAdapter);

        serverList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openServer(serversAdapter.getItem(position));
            }
        });
        serverList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                serverDialog(position);
                return true;
            }
        });
    }

    private void serverDialog(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle((position < 0)?"Add Server":"Edit Server");

        final Integer positionI = position;
        final EditText input = new EditText(this);
        if(position < 0)
            input.setText("http://");
        else
            input.setText(serversAdapter.getItem(position));

        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String server = input.getText().toString();
                if(positionI < 0) {
                    addAndOpenServer(server);
                }
                else{
                    changeServer(positionI, server);
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        if(position>=0)
            builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    removeServer(positionI);
                }
            });

        builder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_server_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_scan_qrcode) {
            startQRscanner();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startQRscanner() {
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        intent.setPackage("com.google.zxing.client.android");
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        startActivityForResult(intent, 0);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                String url = intent.getStringExtra("SCAN_RESULT");
                String format = intent.getStringExtra("SCAN_RESULT_FORMAT");
                // Handle successful scan
                Log.i(PlotVRActivity.TAG, "Got result: "+url+" and format: "+format);

                addAndOpenServer(url);
            } else if (resultCode == RESULT_CANCELED) {
                // nothing
            }
        }
    }

    private void addServer(String url) {
        if(serversAdapter.getPosition(url)>=0)
            return;
        serversAdapter.add(url);
        saveServerList();
        serversAdapter.notifyDataSetChanged();
    }

    private void changeServer(int position, String server) {
        serversAdapter.remove(serversAdapter.getItem(position));
        serversAdapter.insert(server, position);
    }

    private void removeServer(int position) {
        String item = serversAdapter.getItem(position);
        serversAdapter.remove(item);
        saveServerList();
        serversAdapter.notifyDataSetChanged();
        View view = findViewById(R.id.serverList);
        Snackbar.make(view, "Deleted "+item, Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show();
    }

    private void openServer(String contents) {
        Intent intentNew = new Intent(ServerListActivity.this, PlotVRActivity.class);
        if(contents.equals(SAMPLE_DATA))
            intentNew.setData(null);
        else
            intentNew.setData(Uri.parse(contents));
        startActivity(intentNew);
    }

    private void addAndOpenServer(String server) {
        addServer(server);
        openServer(server);
    }

    private void saveServerList() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        sharedPref.edit().putStringSet("servers.list", new TreeSet<String>(serversArray)).apply();
    }

}
