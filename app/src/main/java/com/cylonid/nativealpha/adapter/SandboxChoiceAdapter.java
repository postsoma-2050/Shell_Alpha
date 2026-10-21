package com.cylonid.nativealpha.adapter;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cylonid.nativealpha.R;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.List;

public class SandboxChoiceAdapter extends ArrayAdapter<SandboxChoiceAdapter.Item> {

    public static class Item {
        public final int containerId;
        public final String name;
        public final int color;

        public Item(int containerId, String name, int color) {
            this.containerId = containerId;
            this.name = name;
            this.color = color;
        }

        public int getContainerId() {
            return containerId;
        }

        public String getName() {
            return name;
        }

        public int getColor() {
            return color;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    private final int selectedContainerId;

    public SandboxChoiceAdapter(@NonNull Context context, @NonNull List<Item> items, int selectedContainerId) {
        super(context, R.layout.item_dialog_sandbox_choice, items);
        this.selectedContainerId = selectedContainerId;
    }

    public int getSelectedContainerId() {
        return selectedContainerId;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_dialog_sandbox_choice, parent, false);
        }

        Item item = getItem(position);
        if (item != null) {
            ImageView dot = view.findViewById(R.id.dialogSandboxColorDot);
            TextView name = view.findViewById(R.id.dialogSandboxName);
            MaterialRadioButton radio = view.findViewById(R.id.dialogSandboxRadioButton);

            name.setText(item.name);
            dot.setImageDrawable(createDotDrawable(item.color, getContext()));
            radio.setChecked(item.containerId == selectedContainerId);
            view.setContentDescription(item.name);
        }

        return view;
    }

    private GradientDrawable createDotDrawable(int color, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        int strokeWidth = (int) (1.5f * context.getResources().getDisplayMetrics().density);
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int strokeColor = nightModeFlags == Configuration.UI_MODE_NIGHT_YES ? 0x4DFFFFFF : 0x33000000;
        drawable.setStroke(Math.max(1, strokeWidth), strokeColor);
        return drawable;
    }
}
