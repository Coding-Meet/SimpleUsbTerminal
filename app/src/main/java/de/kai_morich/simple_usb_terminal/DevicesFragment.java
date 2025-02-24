package de.kai_morich.simple_usb_terminal;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayList;
import java.util.Locale;

public class DevicesFragment extends ListFragment {

    static class ListItem {
        UsbDevice device;
        int port;
        UsbSerialDriver driver;

        ListItem(UsbDevice device, int port, UsbSerialDriver driver) {
            this.device = device;
            this.port = port;
            this.driver = driver;
        }
    }

    private final ArrayList<ListItem> listItems = new ArrayList<>();
    private ArrayAdapter<ListItem> listAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        listAdapter = new ArrayAdapter<ListItem>(getActivity(), 0, listItems) {
            @NonNull
            @Override
            public View getView(int position, View view, @NonNull ViewGroup parent) {
                ListItem item = listItems.get(position);
                if (view == null)
                    view = getActivity().getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);
                if(item.driver == null)
                    text1.setText("<no driver>");
                else if(item.driver.getPorts().size() == 1)
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver",""));
                else
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver","")+", Port "+item.port);
                text2.setText(String.format(Locale.US, "Vendor %04X, Product %04X", item.device.getVendorId(), item.device.getProductId()));
                return view;
            }
        };
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(null);
        View header = getActivity().getLayoutInflater().inflate(R.layout.device_list_header, null, false);
        getListView().addHeaderView(header, null, false);
        setEmptyText("<no USB devices found>");
        ((TextView) getListView().getEmptyView()).setTextSize(18);
        setListAdapter(listAdapter);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_devices, menu);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.refresh) {
            refresh();
            return true;
        } else if (id ==R.id.serial_setting) {
            showSerialSettingsDialog();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
    private void showSerialSettingsDialog() {
        Context context = getActivity();
        if (context == null) return;

        final String[] baudRates = getResources().getStringArray(R.array.baud_rates);
        final String[] dataBits = {"5", "6", "7", "8"};
        final String[] stopBits = {"1", "1.5", "2"};
        final String[] parity = {"None", "Odd", "Even", "Mark", "Space"};

        SharedPreferences prefs = context.getSharedPreferences("usb_settings", Context.MODE_PRIVATE);
        int selectedBaud = java.util.Arrays.asList(baudRates).indexOf(String.valueOf(prefs.getInt("baudRate", 19200)));
        int selectedDataBits = java.util.Arrays.asList(dataBits).indexOf(String.valueOf(prefs.getInt("dataBits", UsbSerialPort.DATABITS_8)));
        int selectedStopBits = java.util.Arrays.asList(stopBits).indexOf(String.valueOf(prefs.getInt("stopBits", UsbSerialPort.STOPBITS_1)));
        int selectedParity = prefs.getInt("parity", UsbSerialPort.PARITY_NONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Serial Settings");

        View view = getLayoutInflater().inflate(R.layout.dialog_serial_settings, null);
        Spinner baudRateSpinner = view.findViewById(R.id.spinner_baud);
        Spinner dataBitsSpinner = view.findViewById(R.id.spinner_data_bits);
        Spinner stopBitsSpinner = view.findViewById(R.id.spinner_stop_bits);
        Spinner paritySpinner = view.findViewById(R.id.spinner_parity);

        ArrayAdapter<String> adapterBaud = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, baudRates);
        ArrayAdapter<String> adapterDataBits = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, dataBits);
        ArrayAdapter<String> adapterStopBits = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, stopBits);
        ArrayAdapter<String> adapterParity = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, parity);

        baudRateSpinner.setAdapter(adapterBaud);
        baudRateSpinner.setSelection(selectedBaud);
        dataBitsSpinner.setAdapter(adapterDataBits);
        dataBitsSpinner.setSelection(selectedDataBits);
        stopBitsSpinner.setAdapter(adapterStopBits);
        stopBitsSpinner.setSelection(selectedStopBits);
        paritySpinner.setAdapter(adapterParity);
        paritySpinner.setSelection(selectedParity);

        builder.setView(view);
        builder.setPositiveButton("Save", (dialog, which) -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("baudRate", Integer.parseInt(baudRates[baudRateSpinner.getSelectedItemPosition()]));
            editor.putInt("dataBits", Integer.parseInt(dataBits[dataBitsSpinner.getSelectedItemPosition()]));
            editor.putInt("stopBits", stopBitsSpinner.getSelectedItemPosition() == 0 ? UsbSerialPort.STOPBITS_1 :
                    stopBitsSpinner.getSelectedItemPosition() == 1 ? UsbSerialPort.STOPBITS_1_5 :
                            UsbSerialPort.STOPBITS_2);
            editor.putInt("parity", paritySpinner.getSelectedItemPosition());
            editor.apply();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    void refresh() {
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
        UsbSerialProber usbCustomProber = CustomProber.getCustomProber();
        listItems.clear();
        for(UsbDevice device : usbManager.getDeviceList().values()) {
            UsbSerialDriver driver = usbDefaultProber.probeDevice(device);
            if(driver == null) {
                driver = usbCustomProber.probeDevice(device);
            }
            if(driver != null) {
                for(int port = 0; port < driver.getPorts().size(); port++)
                    listItems.add(new ListItem(device, port, driver));
            } else {
                listItems.add(new ListItem(device, 0, null));
            }
        }
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        ListItem item = listItems.get(position-1);
        if(item.driver == null) {
            Toast.makeText(getActivity(), "no driver", Toast.LENGTH_SHORT).show();
        } else {
            SharedPreferences prefs = getActivity().getSharedPreferences("usb_settings", Context.MODE_PRIVATE);
            int baudRate = prefs.getInt("baudRate", 19200);
            int dataBits = prefs.getInt("dataBits", UsbSerialPort.DATABITS_8);
            int stopBits = prefs.getInt("stopBits", UsbSerialPort.STOPBITS_1);
            int parity = prefs.getInt("parity", UsbSerialPort.PARITY_NONE);

            Bundle args = new Bundle();
            args.putInt("device", item.device.getDeviceId());
            args.putInt("port", item.port);
            args.putInt("baud", baudRate);
            args.putInt("dataBits", dataBits);
            args.putInt("stopBits", stopBits);
            args.putInt("parity", parity);
            Fragment fragment = new TerminalFragment();
            fragment.setArguments(args);
            getParentFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
        }
    }

}
