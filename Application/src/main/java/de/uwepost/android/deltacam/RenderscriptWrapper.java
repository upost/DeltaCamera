/*
 * Copyright 2018 Uwe Post
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.uwepost.android.deltacam;

import android.graphics.ImageFormat;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Size;
import android.view.Surface;

import de.uwepost.yuv2rgb.ScriptC_yuv2rgb;

public class RenderscriptWrapper implements Allocation.OnBufferAvailableListener {
    private Allocation mInputAllocation;
    private Allocation mOutputAllocation;
    private Allocation mOutputAllocationInt;
    private Allocation mScriptAllocation;
    private Allocation mFirstFrameAllocation;
    private Size mSizeVideoCall;
    private ScriptC_yuv2rgb mScriptC;

    private int[] mOutBufferInt;
    private long mLastProcessed;

    private OnFrameAvailableListener mFrameCallback;

    private boolean firstRun=true;

    private final int mFrameEveryMs;
    private boolean reset=false;

    public RenderscriptWrapper(RenderScript rs, Size dimensions, OnFrameAvailableListener callback, int frameMs) {
        mSizeVideoCall = dimensions;
        mFrameCallback = callback;
        mFrameEveryMs = frameMs;


        createAllocations(rs);

        mInputAllocation.setOnBufferAvailableListener(this);

        mScriptC = new ScriptC_yuv2rgb(rs);
        mScriptC.set_gCurrentFrame(mInputAllocation);
        mScriptC.set_gIntFrame(mOutputAllocationInt);
        mScriptC.set_gFirstFrame(mFirstFrameAllocation);
    }

    private void createAllocations(RenderScript rs) {

        mOutBufferInt =  new int[mSizeVideoCall.getWidth() * mSizeVideoCall.getHeight()];

        final int width = mSizeVideoCall.getWidth();
        final int height = mSizeVideoCall.getHeight();

        Type.Builder yuvTypeBuilder = new Type.Builder(rs, Element.YUV(rs));
        yuvTypeBuilder.setX(width);
        yuvTypeBuilder.setY(height);
        yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
        mInputAllocation = Allocation.createTyped(rs, yuvTypeBuilder.create(),  Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

        Type rgbType = Type.createXY(rs, Element.RGBA_8888(rs), width, height);
        Type intType = Type.createXY(rs, Element.U32(rs), width, height);

        mScriptAllocation = Allocation.createTyped(rs, rgbType,   Allocation.USAGE_SCRIPT);

        // dummy allocation because it may not be null
        mOutputAllocation = Allocation.createTyped(rs, rgbType,  Allocation.USAGE_SCRIPT);

        mFirstFrameAllocation = Allocation.createTyped(rs, intType,  Allocation.USAGE_SCRIPT);

        // actual output allocation
        mOutputAllocationInt = Allocation.createTyped(rs, intType, Allocation.USAGE_SCRIPT);
    }

    public Surface getInputSurface() {
        return mInputAllocation.getSurface();
    }

    public void setThreshold(short t) {
        mScriptC.set_threshold(t);
    }

    public short getThreshold() {
        return mScriptC.get_threshold();
    }

    public boolean getDarken() {
        return mScriptC.get_darken()>0;
    }

    public boolean getLighten() {
        return mScriptC.get_lighten()>0;
    }

    public void setDarken() {
        mScriptC.set_darken((short)1);
        mScriptC.set_lighten((short)0);
    }
    public void setLighten() {
        mScriptC.set_darken((short)0);
        mScriptC.set_lighten((short)1);
    }

    @Override
    public void onBufferAvailable(Allocation a) {
        // Get the new frame into the input allocation
        mInputAllocation.ioReceive();

        if(reset) { firstRun=true; reset=false;}

        // Run processing pass if we should send a frame
        final long current = System.currentTimeMillis();
        if ((current - mLastProcessed) >= mFrameEveryMs) {
            mScriptC.set_firstRun(firstRun?(short)1:0);
            mScriptC.forEach_yuv2rgbFrames(mScriptAllocation, mOutputAllocation);
            if (mFrameCallback != null) {
                mOutputAllocationInt.copyTo(mOutBufferInt);
                mFrameCallback.onFrameArrayInt(mOutBufferInt);
            }
            mLastProcessed = current;
            firstRun=false;
        }
    }

    public void reset() {
        reset=true;
    }
}
