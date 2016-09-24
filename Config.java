////////////////////////////////////////////////////////////////////////////////
// Java Graphing Program (graphj)                                             //
// Copyright (c) 07 Sep 2015 Paul J. Gartside                                 //
////////////////////////////////////////////////////////////////////////////////
// Permission is hereby granted, free of charge, to any person obtaining a    //
// copy of this software and associated documentation files (the "Software"), //
// to deal in the Software without restriction, including without  limitation //
// the rights to use, copy, modify, merge, publish, distribute, sublicense,   //
// and/or sell copies of the Software, and to permit persons to whom the      //
// Software is furnished to do so, subject to the following conditions:       //
//                                                                            //
// The above copyright notice and this permission notice shall be included in //
// all copies or substantial portions of the Software.                        //
//                                                                            //
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR //
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,   //
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL    //
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER //
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING    //
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER        //
// DEALINGS IN THE SOFTWARE.                                                  //
////////////////////////////////////////////////////////////////////////////////

import java.util.ArrayList;

class Config
{
  // These parameters are read in from the input file:
  String    Title = "No Title";
  String  X_Title = "";
  String  Y_Title = "";
  String L1_Title = "";
  String L2_Title = "";
  String L3_Title = "";
  String L4_Title = "";

  ArrayList<Double> pX  = new ArrayList<>();
  ArrayList<Double> pY1 = new ArrayList<>();
  ArrayList<Double> pY2 = new ArrayList<>();
  ArrayList<Double> pY3 = new ArrayList<>();
  ArrayList<Double> pY4 = new ArrayList<>();

  // These parameters change every time the window is resized:
  int win_width  = 0;
  int win_height = 0;

  int    x_legend_h = 0; // Graticle legend and X-Title height
  int    y_legend_w = 0; // Graticle legend and Y-Title width
  int line_legend_h = 0;

  int x_box_st() { return y_legend_w; }
  int x_box_fn() { return (int)(0.95*win_width); }
  int y_box_st = 0;
  int y_box_fn()
  {
    return win_height - x_legend_h - line_legend_h;
  }

  double x_min_all; // Min x data value
  double x_max_all; // Max x data value
  double y_min_all; // Min y data value
  double y_max_all; // Max y data value
  double x_min;     // Currently displayed Min x data value
  double x_max;     // Currently displayed Max x data value
  double y_min;     // Currently displayed Min y data value
  double y_max;     // Currently displayed Max y data value
  double tick_x;
  double tick_y;

  double sz_x()
  {
    Utils.Assert( x_min <= x_max, "x_max < x_min");

    if( x_max == x_min ) { x_max += 1; x_min -= 1; }
    return x_max - x_min;
  }
  double sz_y()
  {
    Utils.Assert( y_min <= y_max, "y_max < y_min");

    if( y_max == y_min ) { y_max += 1; y_min -= 1; }
    return y_max - y_min;
  }
  void Clear()
  {
       Title = "No Title";
     X_Title = "";
     Y_Title = "";
    L1_Title = "";
    L2_Title = "";
    L3_Title = "";
    L4_Title = "";

    pX .clear();
    pY1.clear();
    pY2.clear();
    pY3.clear();
    pY4.clear();

    win_width  = 0;
    win_height = 0;

       x_legend_h = 0;
       y_legend_w = 0;
    line_legend_h = 0;

    x_min_all = 0;
    x_max_all = 0;
    y_min_all = 0;
    y_max_all = 0;
    x_min     = 0;
    x_max     = 0;
  //y_min     = 0;
  //y_max     = 0;
    tick_x    = 0;
    tick_y    = 0;
  }
}

