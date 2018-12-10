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
#pragma version(1)
#pragma rs java_package_name(de.uwepost.yuv2rgb)
#pragma rs_fp_relaxed

rs_allocation gCurrentFrame;
rs_allocation gIntFrame;
rs_allocation gFirstFrame;

uint8_t firstRun = 0;

uint8_t threshold = 50;

uint8_t lighten=1;
uint8_t darken=0;



uchar4 __attribute__((kernel)) yuv2rgbFrames(uchar4 prevPixel,uint32_t x,uint32_t y)
{

    // Read in pixel values from latest frame - YUV color space
    // The functions rsGetElementAtYuv_uchar_? require API 18
    uchar4 curPixel;
    curPixel.r = rsGetElementAtYuv_uchar_Y(gCurrentFrame, x, y);
    curPixel.g = rsGetElementAtYuv_uchar_U(gCurrentFrame, x, y);
    curPixel.b = rsGetElementAtYuv_uchar_V(gCurrentFrame, x, y);

    // This function uses the NTSC formulae to convert YUV to RBG
    uchar4 out = rsYuvToRGBA_uchar4(curPixel.r, curPixel.g, curPixel.b);

    // TEST CODE
    // calc avg
//    int avg = (out.b+out.r+out.g)/3;
    // remove colors if red is not dominant
//    if(out.r < (avg+avg/4)) {
//        out.g = out.b = out.r = avg;
//    }

    if(firstRun) {
        rsSetElementAt_int(gFirstFrame, 0xff000000 | out.r << 16 | out.g << 8 | out.b, x, y);
        rsSetElementAt_int(gIntFrame, 0xff000000 | out.r << 16 | out.g << 8 | out.b, x, y);
    } else {
        // get pixel from first frame
        uint32_t first = rsGetElementAt_int(gFirstFrame,x,y);
        uchar4 firstPixel;
        firstPixel.r = (first>>16)&0xff;
        firstPixel.g = (first>>8)&0xff;
        firstPixel.b = (first)&0xff;

        // get pixel from last frame
        uint32_t last = rsGetElementAt_int(gIntFrame,x,y);
        uchar4 lastPixel;
        lastPixel.r = (last>>16)&0xff;
        lastPixel.g = (last>>8)&0xff;
        lastPixel.b = (last)&0xff;

        uint8_t dirty=0;
        if(darken) {
            if(firstPixel.r-out.r>threshold) { dirty=1;}
            if(firstPixel.g-out.g>threshold) { dirty=1;}
            if(firstPixel.b-out.b>threshold) { dirty=1;}
            if(dirty) {
                // if has been darker...
                if(out.r>lastPixel.r || out.g>lastPixel.g || out.b>lastPixel.b) {
                    // do not overwrite.
                    dirty=0;
                }
            }
        } else if(lighten) {
            if(out.r-firstPixel.r>threshold) { dirty=1; }
            if(out.g-firstPixel.g>threshold) { dirty=1; }
            if(out.b-firstPixel.b>threshold) { dirty=1; }
            if(dirty) {
                // if has been lighter ...
                if(out.r<lastPixel.r || out.g<lastPixel.g || out.b<lastPixel.b) {
                    // do not overwrite.
                    dirty=0;
                }
            }
        }
        if(dirty) {
            rsSetElementAt_int(gIntFrame, 0xff000000 | out.r << 16 | out.g << 8 | out.b, x, y);
        }

    }

    return out;
}