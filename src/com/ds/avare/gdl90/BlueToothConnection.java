/*
Copyright (c) 2012, Zubair Khan (governer@gmail.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.ds.avare.gdl90;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;

/**
 * 
 * @author zkhan
 *
 */
public class BlueToothConnection {

    private static BluetoothAdapter mBtAdapter = null;
    private static BluetoothSocket mBtSocket = null;
    private static InputStream mStream = null;
    private static boolean mConnected = false;
    private static boolean mRunning = false;
    private static BlueToothConnectionInterface mListener;
    
    private static BlueToothConnection mConnection;
    
    private static final String mStoreFile = "/sdcard/avare1/adsb-bin.dat";
    

    /*
     *  Well known SPP UUID
     */
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /**
     * 
     */
    private BlueToothConnection() {
    }
    
    /**
     * 
     * @return
     */
    public static BlueToothConnection getInstance() {
        if(null == mConnection) {
            mConnection = new BlueToothConnection();
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
            mConnected = false;
        }
        return mConnection;
    }

    /**
     * 
     */
    public void stop() {
        mRunning = false;
    }

    /**
     * 
     */
    public void registerListener(BlueToothConnectionInterface listener) {
        mListener = listener;
    }
    
    /**
     * 
     */
    public void start() {
        mRunning = true;
        
        /*
         * Thread that reads BT
         */
        Thread thread = new Thread() {
            @Override
            public void run() {
                
                byte[] buffer = new byte[32768];
                DataBuffer dbuffer = new DataBuffer(32768);
                Decode decode = new Decode();
                File file;
                BufferedOutputStream bos = null;
                
                /*
                 * Store file for debug? 
                 */
                if(mStoreFile != null) {
                    try {
                        file = new File(mStoreFile);
                        if(!file.exists()){
                            file.createNewFile();
                        }
                        bos = new BufferedOutputStream(new FileOutputStream(file, true));                    
                    }
                    catch (Exception e) {
                    }
                }               
                
                /*
                 * This state machine will keep trying to connect to 
                 * ADBS receiver
                 */
                while(mRunning) {
                    if(!mConnected) {
                        try {
                            Thread.sleep(1000);
                        }
                        catch (Exception e) {
                            
                        }
                        continue;
                    }
                    else {
                        
                        /*
                         * Read.
                         */
                        int red = 0;
                        try {
                            red = read(buffer);
                        }
                        catch (Exception e) {                            
                        }
                        if(red <= 0) {
                            try {
                                Thread.sleep(1000);
                            }
                            catch (Exception e) {
                                
                            }
                            continue;
                        }
                        dbuffer.put(buffer, red);
                     
                        /**
                         * Store data to file for debugging if set to debug
                         */
                        if(mStoreFile != null) {
                            try {
                                bos.write(buffer, 0, red);
                                bos.flush();
                            } 
                            catch (Exception e) {
                            }
                        }
                        
                        byte[] buf;
                        while(null != (buf = dbuffer.get())) {

                            /*
                             * Get packets, decode
                             */
                            com.ds.avare.gdl90.Message m = decode.decode(buf);
                            /*
                             * Post on UI thread.
                             */
                            Message msg = mHandler.obtainMessage();
                            msg.obj = m;
                            mHandler.sendMessage(msg);
                        }
                    }
                }
                if(mStoreFile != null) {
                    try {
                        bos.close();
                    } 
                    catch (Exception e) {
                    }
                }
            }
            
        };
        thread.start();
    }
    
    /**
     * 
     * A device name devNameMatch, will connect to first device whose
     * name matched this string.
     * @return
     */
    public boolean connect(String devNameMatch) {
        if(null == mBtAdapter) {
            return false;
        }
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        /*
         * Find device
         */
        if(null == pairedDevices) {
            return false;
        }
        BluetoothDevice device = null;
        for(BluetoothDevice bt : pairedDevices) {
           if(bt.getName().contains(devNameMatch)) {
               device = bt;
           }
        }
   
        /*
         * Stop discovery
         */
        mBtAdapter.cancelDiscovery();
 
        if(null == device) {
            Logger.Logit("No connect to BT");
            return false;
        }
        
        /*
         * Make socket
         */
        try {
            mBtSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
        } 
        catch(Exception e) {
            return false;
        }
    
        /*
         * Establish the connection.  This will block until it connects.
         */
        try {
            mBtSocket.connect();
        } 
        catch(Exception e) {
            try {
                mBtSocket.close();
            } 
            catch(Exception e2) {
            }
            return false;
        } 

        try {
            mStream = mBtSocket.getInputStream();
        } 
        catch (Exception e) {
            try {
                mBtSocket.close();
            } 
            catch(Exception e2) {
            }
            mConnected = false;
        } 

        mConnected = true;
        return true;
    }
    
    /**
     * 
     * @return
     */
    private int read(byte[] buffer) {
        int red = -1;
        try {
            red = mStream.read(buffer, 0, buffer.length);
        } 
        catch(Exception e) {
            mConnected = false;
        }
        return red;
    }

    /**
     * 
     * @return
     */
    public boolean isConnected() {
        return mConnected;
    }
    
    /**
     * 
     */
    public void disconnect() {
        try {
            mStream.close();
        } 
        catch(Exception e2) {
        }
        
        try {
            mBtSocket.close();
        } 
        catch(Exception e2) {
        }    
        mConnected = false;

    }
    
    /**
     * Send a message to the lone listener.
     */
    private static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {            
            com.ds.avare.gdl90.Message m = (com.ds.avare.gdl90.Message)msg.obj;
            if(mListener != null) {
                mListener.messageCallback(m);
            }
        }
    };
}