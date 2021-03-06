package com.voxeet.toolkit.providers.containers;

import android.content.Context;
import android.support.annotation.NonNull;

import com.voxeet.toolkit.providers.logics.IVoxeetSubViewProvider;
import com.voxeet.toolkit.implementation.overlays.OverlayState;
import com.voxeet.toolkit.implementation.overlays.VoxeetOverlayToggleView;
import com.voxeet.toolkit.implementation.overlays.abs.AbstractVoxeetOverlayView;
import com.voxeet.toolkit.implementation.overlays.abs.IExpandableViewProviderListener;

/**
 * Created by kevinleperf on 26/01/2018.
 */

public class DefaultConferenceProvider implements IVoxeetOverlayViewProvider {

    private IExpandableViewProviderListener mListener;

    protected IExpandableViewProviderListener getListener() {
        return mListener;
    }

    private DefaultConferenceProvider() {

    }

    public DefaultConferenceProvider(IExpandableViewProviderListener listener) {
        this();

        mListener = listener;
    }

    @NonNull
    @Override
    public AbstractVoxeetOverlayView createView(@NonNull Context context,
                                                @NonNull IVoxeetSubViewProvider provider,
                                                @NonNull OverlayState overlayState) {
        return new VoxeetOverlayToggleView(mListener, provider, context, overlayState);
    }
}
