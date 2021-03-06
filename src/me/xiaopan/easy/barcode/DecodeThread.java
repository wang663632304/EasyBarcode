package me.xiaopan.easy.barcode;

import java.io.ByteArrayOutputStream;

import me.xiaopan.easy.android.util.CameraUtils;
import me.xiaopan.easy.java.util.CircleList;
import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

/**
 * 解码线程
 */
public class DecodeThread extends Thread{
	private boolean running;
	private Decoder decoder;
	private DecodeListener decodeListener;
	private CircleList<byte[]> yuvSources;
	
	public DecodeThread(Decoder decoder, DecodeListener decodeListener) {
		this.decoder = decoder;
		this.decodeListener = decodeListener;
		yuvSources = new CircleList<byte[]>(1);
	}
	
	@Override
	public void run(){
		running = true;
		if(decodeListener != null){
			byte[] yuvSource;
			while(running){
				yuvSource = yuvSources.poll();
				if(yuvSource != null){
					/* 初始化源数据，如果是竖屏的话就将源数据旋转90度 */
					int previewWidth = decoder.getCameraPreviewSize().width;
					int previewHeight = decoder.getCameraPreviewSize().height;
					if (decoder.isPortrait()) {
						yuvSource = CameraUtils.yuvLandscapeToPortrait(yuvSource, previewWidth, previewHeight);
						previewWidth = previewWidth + previewHeight;
						previewHeight = previewWidth - previewHeight;
						previewWidth = previewWidth - previewHeight;
					}
					
					/* 解码 */
					Result decodeResult = null;
					PlanarYUVLuminanceSource planarYUVLuminanceSource = new PlanarYUVLuminanceSource(yuvSource, previewWidth, previewHeight, decoder.getScanningAreaRect().left, decoder.getScanningAreaRect().top, decoder.getScanningAreaRect().width(), decoder.getScanningAreaRect().height(), false);
					try {
						decodeResult = decoder.getMultiFormatReader().decodeWithState(new BinaryBitmap(new HybridBinarizer(planarYUVLuminanceSource)));
					} catch (Exception re) {
						re.printStackTrace();
					} finally {
						decoder.getMultiFormatReader().reset();
					}
					
					/* 解码结果处理 */
					if (decodeResult != null) {
						if(decodeListener != null){
							int[] pixels = planarYUVLuminanceSource.renderThumbnail();
							int width = planarYUVLuminanceSource.getThumbnailWidth();
							int height = planarYUVLuminanceSource.getThumbnailHeight();
							Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
							ByteArrayOutputStream out = new ByteArrayOutputStream();    
							bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
							bitmap.recycle();
							decodeListener.onDecodeSuccess(decodeResult, out.toByteArray(), (float) width / planarYUVLuminanceSource.getWidth());
						}
					} else {
						if(decodeListener != null){
							decodeListener.onDecodeFailure();
						}
					}
				}else{
					synchronized (yuvSources) {
						try {
							yuvSources.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
	
	/**
	 * 终止线程
	 */
	public void finish(){
		running = false;
		synchronized (yuvSources) {
			try{
				yuvSources.clear();
				yuvSources.notify();
			}catch(Exception exception){
				exception.printStackTrace();
			}
		}
	}
	
	/**
	 * 暂停解码
	 */
	public void pause(){
		synchronized (yuvSources) {
			yuvSources.clear();
		}
	}
	
	/**
	 * 尝试解码
	 * @param yuvSource
	 */
	public void tryDecode(byte[] yuvSource) {
		synchronized (yuvSources) {
			yuvSources.add(yuvSource);
			try{
				yuvSources.notify();
			}catch(Exception exception){
				exception.printStackTrace();
			}
		}
	}
}