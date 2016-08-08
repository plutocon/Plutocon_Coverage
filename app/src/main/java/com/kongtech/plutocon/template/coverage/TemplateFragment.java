package com.kongtech.plutocon.template.coverage;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.kongtech.plutocon.sdk.Plutocon;
import com.kongtech.plutocon.sdk.PlutoconManager;
import com.kongtech.plutocon.template.PlutoconListActivity;
import com.kongtech.plutocon.template.view.AttrItemView;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TemplateFragment extends Fragment implements View.OnClickListener {

    private final int OFFSET_RSSI = -100;

    private AttrItemView[] aivTargetName = new AttrItemView[3];
    private AttrItemView[] aivTargetAddress = new AttrItemView[3];
    private AttrItemView[] aivTargetStatus = new AttrItemView[3];
    private View[] progress = new View[3];

    private TextView tvRssi;
    private SeekBar sbRssi;

    private Snackbar snackbar;

    private PlutoconManager plutoconManager;
    private Plutocon[] targetPlutocon = new Plutocon[3];
    private int targetRssi = -40;
    private boolean[] isDiscovered = new boolean[3];

    private Timer refreshTimer;

    public static Fragment newInstance(Context context) {
        TemplateFragment f = new TemplateFragment();
        return f;
    }

    public void startMonitoring() {
        plutoconManager.startMonitoring(PlutoconManager.MONITORING_FOREGROUND, new PlutoconManager.OnMonitoringPlutoconListener() {
            @Override
            public void onPlutoconDiscovered(Plutocon plutocon, List<Plutocon> plutocons) {
                for (int i = 0; i < 3; i++) {
                    if (targetPlutocon[i] != null
                            && plutocon.getMacAddress().equals(targetPlutocon[i].getMacAddress())
                            && plutocon.getRssi() > targetRssi) {

                        targetPlutocon[i] = plutocon;

                        if (!isDiscovered[i]) {
                            final int finalI = i;
                            Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                            vibrator.vibrate(300);
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setStatus(finalI, true);
                                }
                            });
                        }
                    }
                }
            }
        });
    }

    private void setStatus(int position, boolean isDiscovered) {
        this.isDiscovered[position] = isDiscovered;
        if (isDiscovered) {
            aivTargetStatus[position].setValue("Discovered");
            aivTargetStatus[position].setAttrValueColor(getResources().getColor(R.color.appleGreen));
        } else {
            aivTargetStatus[position].setValue("Not discovered");
            aivTargetStatus[position].setAttrValueColor(0xffff0000);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (targetPlutocon != null) {
            this.startMonitoring();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        plutoconManager.stopMonitoring();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        plutoconManager.close();
        refreshTimer.cancel();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.content_template, null);

        this.setUpView((ViewGroup) view.findViewById(R.id.target1), 0);
        this.setUpView((ViewGroup) view.findViewById(R.id.target2), 1);
        this.setUpView((ViewGroup) view.findViewById(R.id.target3), 2);

        tvRssi = (TextView) view.findViewById(R.id.tvRSSI);
        sbRssi = (SeekBar) view.findViewById(R.id.sbRSSI);
        sbRssi.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                targetRssi = OFFSET_RSSI + progress;
                tvRssi.setText(targetRssi + "dBm");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        return view;
    }

    public void setUpView(ViewGroup viewGroup, int position) {
        aivTargetName[position] = (AttrItemView) viewGroup.findViewById(R.id.aivTargetName);
        aivTargetAddress[position] = (AttrItemView) viewGroup.findViewById(R.id.aivTargetAddress);
        aivTargetStatus[position] = (AttrItemView) viewGroup.findViewById(R.id.aivTargetStatus);
        progress[position] = viewGroup.findViewById(R.id.progress);
        progress[position].setVisibility(View.INVISIBLE);
        ((ProgressBar) progress[position]).getIndeterminateDrawable().setColorFilter(
                0xffffffff,
                android.graphics.PorterDuff.Mode.SRC_IN);
        aivTargetName[position].setId(position);
        aivTargetName[position].setOnClickListener(this);

    }

    private TimerTask getRefreshTimer(){
        return new TimerTask() {
            @Override
            public void run() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long ms = System.currentTimeMillis();
                        for (int i = 0; i < 3; i++) {
                            if (targetPlutocon[i] != null
                                    && isDiscovered[i]
                                    && ms - targetPlutocon[i].getLastSeenMillis() >= 2000) {
                                setStatus(i, false);
                            }
                        }
                    }
                });
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();

        plutoconManager = new PlutoconManager(this.getContext());
        plutoconManager.connectService(null);

        sbRssi.setProgress(60);

        refreshTimer = new Timer();
        refreshTimer.schedule(getRefreshTimer(), 0, 100);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (checkPermission())
            startActivityForResult(new Intent(getActivity(), PlutoconListActivity.class), id);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        int position = requestCode;
        if (resultCode == 1) {
            targetPlutocon[position] = (Plutocon) data.getParcelableExtra("PLUTOCON");
            aivTargetName[position].setValue(targetPlutocon[position].getName());
            aivTargetAddress[position].setValue(targetPlutocon[position].getMacAddress());
            progress[position].setVisibility(View.VISIBLE);
            this.setStatus(position, false);
            this.startMonitoring();
        }
    }

    private boolean checkPermission() {
        BluetoothManager bluetoothManager =
                (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        if ((mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())) {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return false;
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getActivity().checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                return false;
            }

            LocationManager lm = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
            boolean gps_enabled = false;
            try {
                gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (Exception ex) {
            }

            if (!gps_enabled) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                return false;
            }
        }
        return true;
    }
}
