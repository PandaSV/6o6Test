package com.panda.a6o6test.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.panda.a6o6test.R;
import com.panda.a6o6test.logic.MainStateListener;
import com.panda.a6o6test.logic.MainUiStateMachine;

/**
 * UI part showing "instructions" and buttons.
 * Should have been a transparent overlay, but it would take extra time.
 */
public class PanelFragment extends Fragment implements MainStateListener {

    private ViewFlipper viewFlipper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.panel_fragment, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewFlipper = view.findViewById(R.id.panel_flipper);
        view.findViewById(R.id.next_button).setOnClickListener(v -> MainUiStateMachine.getInstance().toNoFaceFromButton());
        view.findViewById(R.id.to_test_button).setOnClickListener(v -> MainUiStateMachine.getInstance().toTest());
    }

    @Override
    public void onResume() {
        super.onResume();
        MainUiStateMachine.getInstance().addStateListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        MainUiStateMachine.getInstance().removeListener(this);
    }

    @Override
    public void onStateChanged(MainUiStateMachine.MainLogicState state) {
        if(getActivity() != null) {
            getActivity().runOnUiThread(() -> viewFlipper.setDisplayedChild(viewFlipper.indexOfChild(viewFlipper.findViewById(StateViewMap.getIdForState(state)))));
        }
    }

}
