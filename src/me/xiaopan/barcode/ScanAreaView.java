/*
 * Copyright 2013 Peng fei Pan
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.xiaopan.barcode;

import java.util.Collection;
import java.util.HashSet;

import me.xiaopan.easy.android.util.AndroidLogger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;

/**
 * 扫描区域视图
 */
public class ScanAreaView extends View implements Runnable{
	private static final int OPAQUE = 0xFF;//不透明
	private int resultPointColor = -256;	//可疑点的颜色，默认为黄色
	private float newResultPointRadius = 6.0f;	//新的可疑点半径
	private float lastResultPointRadius = 3.0f;	//旧的可疑点半径
	private int laserLineColor = -65536;	//激光线的颜色，默认为红色
	private int laserLineSlidePace = 5;	//激光线滑动步伐
	private int laserLineHeight = 4;//激光线高度
	private int laserLineInSlideAvailableHeight;	//激光线在滑动的过程中扫描框可使用的高度
	private int laserLineLeft;	//激光线左边距
	private int laserLineTop;	//激光线顶边距（固定位置时使用）
	private int laserLineSlideTop;	//激光线在滑动过程中的顶边距（动态变化的）
	private int laserLineRight;	//激光线的右边距
	private int laserLineBottom;	//激光线的底边距
	private int laserLineAlphaIndex;	//激光线透明度索引
	private int[] laserLineAlphas = {120, 140, 160, 180, 200, 220, 240, 255, 250, 230, 210, 190, 170, 150, 130, 110};//激光线透明度变化表
	private boolean closeSlideLaserLineMode;	//是否关闭滑动激光线模式
	private int width;	//扫描框的宽
	private int height;	//扫描框的高
	private int strokeWidth;	//描边的宽度
	private Paint paint;	//画笔
	private Bitmap resultBitmap;	//扫描结果图片
	private boolean init = true;	//初始化位置信息
	private Collection<ResultPoint> possibleResultPoints;	//当前可疑点集合
	private Collection<ResultPoint> lastPossibleResultPoints;	//上次可疑点集合
	private Rect rect;
	private Handler handler;
	
	public ScanAreaView(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint = new Paint();
		handler = new Handler();
		possibleResultPoints = new HashSet<ResultPoint>(5);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		AndroidLogger.e("onDraw");
		/* 初始化 */
		if(init){
			init = false;
			width = getWidth();
			height = getHeight();
			laserLineInSlideAvailableHeight = (height - getLaserLineHeight());
			laserLineLeft = getStrokeWidth();
			laserLineTop = (height/2) - (getLaserLineHeight() / 2);
			laserLineRight = width - getStrokeWidth();
			laserLineBottom = (height/2) +(getLaserLineHeight() / 2);
		}
		
		//如果有结果图片就直接将结果图片绘制到扫描框中
		if (resultBitmap != null) {
			paint.setAlpha(OPAQUE);
			if(rect == null){
				rect = new Rect(0, 0, getWidth(), getHeight());
			}
			canvas.drawBitmap(resultBitmap, null, rect, paint);
		} else {
			/* 绘制激光线 */
			paint.setColor(getLaserLineColor());	//设置画笔颜色为红色
			paint.setAlpha(getLaserLineAlphas()[laserLineAlphaIndex]);	//设置透明度（透明度的值根据索引从透明度变换表中取）
			laserLineAlphaIndex = (laserLineAlphaIndex + 1) % getLaserLineAlphas().length;	//更新激光线透明度索引
			if(isCloseSlideLaserLineMode()){
				canvas.drawRect(laserLineLeft, laserLineTop, laserLineRight, laserLineBottom, paint);	//绘制激光线
			}else{
				canvas.drawRect(laserLineLeft, laserLineSlideTop, laserLineRight, laserLineSlideTop + laserLineHeight, paint);	//绘制激光线
				laserLineSlideTop = (laserLineSlideTop + getLaserLineSlidePace()) % laserLineInSlideAvailableHeight;	//更新顶边距
			}
			
			/* 绘制可疑点 */
			Collection<ResultPoint> currentPossible = possibleResultPoints;
			Collection<ResultPoint> currentLast = lastPossibleResultPoints;
			if (currentPossible.isEmpty()) {
				lastPossibleResultPoints = null;
			} else {
				possibleResultPoints = new HashSet<ResultPoint>(5);
				lastPossibleResultPoints = currentPossible;
				paint.setAlpha(OPAQUE);
				paint.setColor(getResultPointColor());
				for (ResultPoint point : currentPossible) {
					canvas.drawCircle(point.getX(), point.getY(), getNewResultPointRadius(), paint);
				}
			}
			if (currentLast != null) {
				paint.setAlpha(OPAQUE / 2);
				paint.setColor(resultPointColor);
				for (ResultPoint point : currentLast) {
					canvas.drawCircle(point.getX(), point.getY(), getLastResultPointRadius(), paint);
				}
			}
		}
	}
	
	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		AndroidLogger.e("onLayout");
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if(resultBitmap != null && !resultBitmap.isRecycled()){
			resultBitmap.recycle();
		}
	}

	/**
	 * 添加可疑点
	 * @param point
	 */
	public void addPossibleResultPoint(ResultPoint point) {
		possibleResultPoints.add(point);
	}
	
	/**
	 * 刷新
	 */
	public void refresh() {
		resultBitmap = null;
		invalidate();
	}

	/**
	 * 绘制结果图片
	 */
	public void drawResultBitmap(Bitmap barcode) {
		if(resultBitmap != null && !resultBitmap.isRecycled()){
			resultBitmap.recycle();
		}
		resultBitmap = barcode;
		invalidate();
	}
	
	@Override
	public void run() {
		refresh();
		handler.postDelayed(this, 50);
	}
	
	/**
	 * 开始刷新（移动并闪烁激光线以及显示可疑点）
	 */
	public void startRefresh(){
		handler.post(this);
	}
	
	/**
	 * 停止刷新
	 */
	public void stopRefresh(){
		handler.removeCallbacks(this);
	}

	/**
	 * 获取描边的宽度
	 * @return 描边的宽度
	 */
	public int getStrokeWidth() {
		return strokeWidth;
	}

	/**
	 * 设置描边的宽度
	 * @param strokeWidth 描边的宽度
	 */
	public void setStrokeWidth(int strokeWidth) {
		this.strokeWidth = strokeWidth;
		init = true;
	}

	/**
	 * 判断是否关闭滑动激光线模式
	 * @return 是否关闭滑动激光线模式
	 */
	public boolean isCloseSlideLaserLineMode() {
		return closeSlideLaserLineMode;
	}

	/**
	 * 设置是否关闭滑动激光线模式
	 * @param closeSlideLaserLineMode 是否关闭滑动激光线模式
	 */
	public void setCloseSlideLaserLineMode(boolean closeSlideLaserLineMode) {
		this.closeSlideLaserLineMode = closeSlideLaserLineMode;
	}

	/**
	 * 获取可疑点颜色
	 * @return 可疑点颜色
	 */
	public int getResultPointColor() {
		return resultPointColor;
	}

	/**
	 * 设置可疑点颜色
	 * @param resultPointColor 可疑点颜色
	 */
	public void setResultPointColor(int resultPointColor) {
		this.resultPointColor = resultPointColor;
	}

	/**
	 * 获取激光线颜色
	 * @return 激光线颜色
	 */
	public int getLaserLineColor() {
		return laserLineColor;
	}

	/**
	 * 设置激光线颜色
	 * @param resultPointColor 激光线颜色
	 */
	public void setLaserLineColor(int laserLineColor) {
		this.laserLineColor = laserLineColor;
	}

	/**
	 * 获取新的可疑点的半径
	 * @return 新的可疑点的半径
	 */
	public float getNewResultPointRadius() {
		return newResultPointRadius;
	}

	/**
	 * 设置新的可疑点的半径
	 * @param newResultPointRadius 新的可疑点的半径
	 */
	public void setNewResultPointRadius(float newResultPointRadius) {
		this.newResultPointRadius = newResultPointRadius;
	}

	/**
	 * 获取旧的可疑点的半径
	 * @return 旧的可疑点的半径
	 */
	public float getLastResultPointRadius() {
		return lastResultPointRadius;
	}

	/**
	 * 设置旧的可疑点的半径
	 * @param lastResultPointRadius 旧的可疑点的半径
	 */
	public void setLastResultPointRadius(float lastResultPointRadius) {
		this.lastResultPointRadius = lastResultPointRadius;
	}

	/**
	 * 获取激光线透明度变化表
	 * @return 激光线透明度变化表
	 */
	public int[] getLaserLineAlphas() {
		return laserLineAlphas;
	}

	/**
	 * 设置激光线透明度变化表
	 * @param laserLineAlphas 激光线透明度变化表
	 */
	public void setLaserLineAlphas(int[] laserLineAlphas) {
		this.laserLineAlphas = laserLineAlphas;
	}

	/**
	 * 获取激光线滑动步伐
	 * @return 激光线滑动步伐
	 */
	public int getLaserLineSlidePace() {
		return laserLineSlidePace;
	}

	/**
	 * 设置激光线滑动步伐
	 * @param laserLineSlidePace 激光线滑动步伐
	 */
	public void setLaserLineSlidePace(int laserLineSlidePace) {
		this.laserLineSlidePace = laserLineSlidePace;
	}

	/**
	 * 获取激光线高度
	 * @return 激光线高度
	 */
	public int getLaserLineHeight() {
		return laserLineHeight;
	}

	/**
	 * 设置激光线高度
	 * @param laserLineHeight 激光线高度
	 */
	public void setLaserLineHeight(int laserLineHeight) {
		this.laserLineHeight = laserLineHeight;
		init = true;
	}
}
