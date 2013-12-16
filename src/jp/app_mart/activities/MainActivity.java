package jp.app_mart.activities;

import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

import jp.app_mart.service.AppmartInBillingInterface;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @copyright Appmart(c) �̓����ۋ��V�X�e���T���v���R�[�h�ł��B 
 */
public class MainActivity extends Activity {
	
	// �f�x���b�p�h�c
	public static final String APPMART_DEVELOPER_ID = "your_developer_id";
	// ���C�Z���X�L�[
	public static final String APPMART_LICENSE_KEY = "your_license_key";
	// ���J��
	public static final String APPMART_PUBLIC_KEY = "your_public_key";
	// �A�v���h�c
	public static final String APPMART_APP_ID = "your_application_id";
	// �T�[�r�X�h�c
	public static final String APPMART_SERVICE_ID = "your_service_id";
	
	// aidl�t�@�C�����琶�����ꂽ�T�[�r�X�N���X
	private AppmartInBillingInterface service;
	// �ڑ����
	private boolean isConnected = false;
	// appmart package
	public static final String APP_PACKAGE = "jp.app_mart";
	// �T�[�r�X�p�X
	public static final String APP_PATH = "jp.app_mart.service.AppmartInBillingService";
	
	// �c�d�a�t�f
	private boolean isDebug = true;	
	// �A�v���R���e�L�X�g
	private Context mContext;
	// thread�p��handler
	private Handler handler = new Handler();
	// pendingIntent
	PendingIntent pIntent;
	// ����ID
	private String transactionId;
	//���σL�[
	private String resultKey;
	//���񌈍ςh�c
	private String nextTransactionId;
	// BroadcastReceiver(���ό�j
	private AppmartReceiver receiver;
	private ServiceConnection mConnection;
	
	public static final String RESULT_CODE = "resultCode";
	public static final String RESULT_KEY = "resultKey";
	public static final String PENDING = "appmart_pending_intent";
	public static final String BROADCAST = "appmart_broadcast_return_service_payment";
	public static final String SERVICE_ID = "appmart_service_trns_id";
	public static final String APPMART_RESULT_KEY = "appmart_result_key";	
	public static final String SERVICE_NEXT_ID = "appmart_service_next_trns_id";
	
	TextView success;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mContext = getApplicationContext();

		success = (TextView) findViewById(R.id.success_tv);
		
		// ���ό��broadcast���L���b�`
		setReceiver();

		// appmart�T�[�r�X�ɐڑ����邽�߂�Intent�I�u�W�F�N�g�𐶐�
		Intent i = new Intent();
		i.setClassName(APP_PACKAGE, APP_PATH);
		if (mContext.getPackageManager().queryIntentServices(i, 0).isEmpty()) {
			debugMess(getString(R.string.no_appmart_installed));
			return;
		}

		// Service Connection�C���X�^���X��
		mConnection = new ServiceConnection() {
			
			//�ڑ������s
			public void onServiceConnected(ComponentName name,
					IBinder boundService) {
				//�r�������������N���X���C���X�^���X��
				service = AppmartInBillingInterface.Stub.asInterface((IBinder) boundService);
				isConnected = true;
				debugMess(getString(R.string.appmart_connection_success));
			}
			//�ؒf�����s
			public void onServiceDisconnected(ComponentName name) {
				service = null;
			}
		};

		// bindService�𗘗p���A�T�[�r�X�ɐڑ�
		try {
			bindService(i, mConnection, Context.BIND_AUTO_CREATE);
		} catch (Exception e) {
			e.printStackTrace();
			debugMess(getString(R.string.appmart_connection_not_possible));
		}

		// Handler������
		handler = new Handler() {
			@SuppressLint("HandlerLeak")
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case 1: // pendingIntent�擾
					accessPaymentPage();
					break;
				case 2:// �p�����[�^NG
					debugMess(getString(R.string.wrong_parameters));
					break;
				case 3:// ��O����
					debugMess(getString(R.string.exception_occured));
					break;
				case 10:// ���ύŏI�m�F����					
					TextView success = (TextView) findViewById(R.id.success_tv);
					success.setVisibility(View.VISIBLE);					
					break;
				case -10:// ���ύŏI�m�F�G���[
					debugMess(getString(R.string.settlement_not_confirmed));
					break;
				}
			}
		};
		

		// ���ω�ʂ��Ăԃ{�^��
		Button paymentButton = (Button) findViewById(R.id.access_payment);
		paymentButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				//�ڑ���Ԃ̊m�F
				if (isConnected) {

					debugMess(getString(R.string.start_information_handle));

					(new Thread(new Runnable() {
						public void run() {
							try {

								// �K�v�ȃf�[�^���Í���
								String dataEncrypted = createEncryptedData(
										APPMART_SERVICE_ID,
										APPMART_DEVELOPER_ID,
										APPMART_LICENSE_KEY, APPMART_PUBLIC_KEY);

								// �T�[�r�X��prepareForBillingService���\�b�h���Ăт܂�
								Bundle bundleForPaymentInterface = service.prepareForBillingService(
												APPMART_APP_ID, dataEncrypted);

								if (bundleForPaymentInterface != null) {
									
									int statusId = bundleForPaymentInterface.getInt(RESULT_CODE);
									if (statusId != 1) {
										handler.sendEmptyMessage(2);
										return;
									} else {

										// PendingIntent���擾
										pIntent = bundleForPaymentInterface.getParcelable(PENDING);
										
										// ���σL�[���擾
										resultKey= bundleForPaymentInterface.getString(RESULT_KEY);
										
										// mainUI�ɐݒ�
										handler.sendEmptyMessage(1);
									}

								}

							} catch (Exception e) {
								handler.sendEmptyMessage(3);
								e.printStackTrace();
							}

						}
					})).start();
				}
			}
		});
		
	}
	
	/*�@BroadcastReceiver�̐ݒ� */
	private void setReceiver() {
		// Broadcast�ݒ�
		IntentFilter filter = new IntentFilter(BROADCAST);
		receiver = new AppmartReceiver();
		registerReceiver(receiver, filter);
	}

	/* onDestroy */
	@Override
	protected void onDestroy() {

		super.onDestroy();

		// appmart�T�[�r�X����A���o�C���h
		unbindService(mConnection);
		service = null;

		// broadcast��~
		unregisterReceiver(receiver);

	}

	/* �ۋ���ʂփ��_�C���N�g */
	private void accessPaymentPage() {
		try {
			pIntent.send(mContext, 0, new Intent());
		} catch (CanceledException e) {
			e.printStackTrace();
		}
	}

	/* debug�p */
	private void debugMess(String mess) {
		if (isDebug) {
			Log.d("DEBUG", mess);
			Toast.makeText(getApplicationContext(), mess, Toast.LENGTH_SHORT)
					.show();
		}
	}

	/*���ϊ������broadcast��catch����Receiver�N���X */
	private class AppmartReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context arg0, Intent arg1) {

			try {

				debugMess(getString(R.string.settlement_confirmed));

				// ���ςh�c���擾
				transactionId = arg1.getExtras().getString(SERVICE_ID);
				
				//���σL�[
				String resultKeyCurrentStransaction= arg1.getExtras().getString(APPMART_RESULT_KEY);
				
				//Appmart1.2�ȉ��͌��σL�[�����s����Ȃ�
				if (resultKeyCurrentStransaction==null || resultKeyCurrentStransaction.equals(resultKey)){
								
					// �p�����ς̏ꍇ�͎��񌈍ςh�c���擾
					nextTransactionId = arg1.getExtras().getString(SERVICE_NEXT_ID);
	
					// �R���e���c��񋟂��A�c�a���X�V
					Thread.sleep(1000);
	
					// ���ς��m�F
					(new Thread(new Runnable() {
						public void run() {
	
							try {
	
								int res = service.confirmFinishedTransaction(
										transactionId, APPMART_SERVICE_ID,
										APPMART_DEVELOPER_ID);
	
								if (res == 1) {
									handler.sendEmptyMessage(10);
								} else {
									handler.sendEmptyMessage(-10);
								}
	
							} catch (Exception e) {
								handler.sendEmptyMessage(3);
								e.printStackTrace();
							}
	
						}
					})).start();
				
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/* �����Í��� */
	public String createEncryptedData(String serviceId, String developId,
			String strLicenseKey, String strPublicKey) {

		final String SEP_SYMBOL = "&";
		StringBuilder infoDataSB = new StringBuilder();
		infoDataSB.append(serviceId).append(SEP_SYMBOL);

		// �f�x���b�pID������ǉ�
		infoDataSB.append(developId).append(SEP_SYMBOL);

		// ���C�Z���X�L�[������ǉ�
		infoDataSB.append(strLicenseKey);

		String strEncryInfoData = "";

		try {
			KeyFactory keyFac = KeyFactory.getInstance("RSA");
			KeySpec keySpec = new X509EncodedKeySpec(Base64.decode(
					strPublicKey.getBytes(), Base64.DEFAULT));
			Key publicKey = keyFac.generatePublic(keySpec);

			if (publicKey != null) {
				Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
				cipher.init(Cipher.ENCRYPT_MODE, publicKey);

				byte[] EncryInfoData = cipher.doFinal(infoDataSB.toString()
						.getBytes());
				strEncryInfoData = new String(Base64.encode(EncryInfoData,
						Base64.DEFAULT));
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			strEncryInfoData = "";
			debugMess(getString(R.string.data_encryption_failed));
		}

		return strEncryInfoData.replaceAll("(\\r|\\n)", "");

	}
}
