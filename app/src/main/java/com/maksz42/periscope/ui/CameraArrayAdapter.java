package com.maksz42.periscope.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maksz42.periscope.R;
import com.maksz42.periscope.ui.CameraArrayAdapter.CameraItem;

import java.util.Collections;
import java.util.List;


public class CameraArrayAdapter extends ArrayAdapter<CameraItem> {
  public static class CameraItem {
    public final String name;
    public boolean enabled;
    public boolean audioEnabled;

    public CameraItem(String name, boolean enabled, boolean audioEnabled) {
      this.name = name;
      this.enabled = enabled;
      this.audioEnabled = audioEnabled;
    }

    @NonNull
    @Override
    public String toString() {
      return name;
    }
  }


  private final List<CameraItem> items;
  private final boolean audioEnabled;

  public CameraArrayAdapter(@NonNull Context context, @NonNull List<CameraItem> items, boolean audioEnabled) {
    super(context, R.layout.camera_order_item, R.id.camera_name, items);
    this.items = items;
    this.audioEnabled = audioEnabled;
  }

  private void swapItems(int i, int j) {
    Collections.swap(items, i, j);
    notifyDataSetChanged();
  }

  private void setAudioImg(ImageButton btn, CameraItem cameraItem) {
    if (cameraItem.audioEnabled) {
      btn.setImageResource(android.R.drawable.ic_lock_silent_mode_off);
    } else {
      btn.setImageResource(android.R.drawable.ic_lock_silent_mode);
    }
  }

  @NonNull
  @Override
  public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
    View row = super.getView(position, convertView, parent);

    CameraItem cameraItem = items.get(position);

    CheckBox checkBox = row.findViewById(R.id.camera_enabled);
    // new listener must be set before setChecked()
    checkBox.setOnCheckedChangeListener((v, isChecked) -> cameraItem.enabled = isChecked);
    checkBox.setChecked(cameraItem.enabled);

    ImageButton audioBtn = row.findViewById(R.id.btn_audio);
    if (audioEnabled) {
      audioBtn.setOnClickListener(v -> {
        cameraItem.audioEnabled = !cameraItem.audioEnabled;
        setAudioImg((ImageButton) v, cameraItem);
      });
      setAudioImg(audioBtn, cameraItem);
      audioBtn.setVisibility(View.VISIBLE);
    }

    ImageButton btnUp = row.findViewById(R.id.btn_up);
    btnUp.setEnabled(position != 0);
    btnUp.setOnClickListener(v -> {
      // check necessary for hw buttons
      if (!v.isEnabled()) return;
      swapItems(position, position - 1);
    });

    ImageButton btnDown = row.findViewById(R.id.btn_down);
    btnDown.setEnabled(position != items.size() - 1);
    btnDown.setOnClickListener(v -> {
      // check necessary for hw buttons
      if (!v.isEnabled()) return;
      swapItems(position, position + 1);
    });

    return row;
  }
}
