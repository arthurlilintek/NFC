package book.lab.com.app_nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;


public class MainActivity extends Activity {

    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private EditText mNote;
    private TextView mNFCContent;
    private ImageView mNFCImage;
    private Button mBtn;
    private AlertDialog dialog;
    private Context context;

    private boolean writeMode = false;
    private boolean passByOnNewIntent = false;
    private boolean enabledNFC = false;

    // 關閉 NFC tag 寫入模式
    private void disableTagWriteMode() {
        mAdapter.disableForegroundDispatch(this);
        writeMode = false;
    }

    // 判斷使用者有無開啓 NFC
    private boolean isEnabledNFC() {

        NfcManager manager = (NfcManager) context.getSystemService(Context.NFC_SERVICE);
        NfcAdapter adapter = manager.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            Toast.makeText(context, "NFC 已開啓 !", Toast.LENGTH_SHORT).show();
            return true;
        } else {
            // 開啓 NFC 設定畫面
            Intent intent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
            startActivityForResult(intent, 101);
        }
        return false;
    }

    // App 建立
    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_main);
        context = this;

        // 1.建立 NFC Adapter
        mAdapter = NfcAdapter.getDefaultAdapter(this);

        // 2.創建一個PendingIntent，當掃描NFC目標端標記時 Android 系統可以讀取標記的資訊。
        mPendingIntent = PendingIntent.getActivity(this, 200,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT);

        // 3.宣告IntentFilter來偵聽讀取標記資訊的intent
        IntentFilter tag = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);

        // 4.定義 IntentFilter[] 過濾器
        // 由於每一次發現標記的過程中都會收到TAG_DISCOVERED intent，
        // 因此可藉由定義一個IntentFilter[]來存放所有的IntentFilter物件
        mFilters = new IntentFilter[] {
                tag
        };

        mNote = (EditText)findViewById(R.id.mNote);
        mNFCContent = (TextView)findViewById(R.id.mNFCContent);
        mNFCImage = (ImageView)findViewById(R.id.mNFCImage);
        mBtn = (Button)findViewById(R.id.mBtn);
        mBtn.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                mNFCContent.setText("");
                mNFCImage.setImageResource(R.drawable.icon);
                // Write to a tag for as long as the dialog is shown.
                // enableTagWriteMode();
                writeMode = true;
                dialog = new AlertDialog.Builder(context)
                        .setTitle("請將NFC Tag放到NFC手機裝置感應處!")
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {

                            public void onCancel(DialogInterface dialog) {
                                disableTagWriteMode();
                            }

                        }).create();
                dialog.show();

            }

        });

    }

    // App 啟動
    @Override
    public void onStart() {
        super.onStart();
        enabledNFC = isEnabledNFC();
    }

    // 喚醒再度進入App
    @Override
    public void onResume() {
        super.onResume();

        if(!enabledNFC) return;

        // 啟動前景Activity調度
        mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, null);

        if (!passByOnNewIntent) {
            Toast.makeText(context, getIntent().getAction() + "-onResume()",
                    Toast.LENGTH_SHORT).show();
            // 判斷標簽模式
            if ((NfcAdapter.ACTION_TAG_DISCOVERED.equals(getIntent().getAction()) ||
                    (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())))) {
                Tag detectedTag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
                // 寫入標簽: (欲寫入之記錄 [NdefMessage format], 標簽資訊)
                if(writeMode) {
                    writeTag(getNoteAsNdef(), detectedTag);
                } else {
                    showNFCTagContent(getIntent());
                }
            }
        }
    }

    // 取得最新Intent資訊
    @Override
    public void onNewIntent(Intent intent) {

        if(!enabledNFC) return;

        // 初始UI資料
        passByOnNewIntent = true;
        mNFCContent.setText("");
        mNFCImage.setImageResource(R.drawable.icon);
        Toast.makeText(context, intent.getAction() + "-onNewIntent()",
                Toast.LENGTH_SHORT).show();

        // 判斷標簽模式
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()) ||
                (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()))) {
            Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            // 寫入標簽: (欲寫入之記錄 [NdefMessage format], 標簽資訊)
            if(writeMode) {
                writeTag(getNoteAsNdef(), detectedTag);
            } else {
                showNFCTagContent(intent);
            }
        }
    }

    // 顯示 Ndef Tag內容
    private void showNFCTagContent(Intent intent) {

        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage[] msgs = null;
        if (rawMsgs != null) {
            msgs = new NdefMessage[rawMsgs.length];
            for (int i = 0; i < rawMsgs.length; i++) {
                msgs[i] = (NdefMessage) rawMsgs[i];
            }
            byte[] payload = msgs[0].getRecords()[0].getPayload();
            String value = new String(payload);

            // 顯示NFC內容
            mNFCContent.setText("標記內容 : " + value);
            mNote.setText(value);
            Toast.makeText(this, "標記內容 : " + value, Toast.LENGTH_SHORT).show();

            // 置入NFC內容並開啓browser
             openBrowser(value);
        }

    }

    // 置入NFC內容並開啓browser
    private void openBrowser(String value) {
        String url = "https://www.google.com.tw/search?q=" + value + "&tbm=isch";
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(web);

        // 關閉 Activity
        if (!passByOnNewIntent) {
            finish();
        }
    }

    // 取得 NdefMessage
    private NdefMessage getNoteAsNdef() {
        // 1.將輸入的字串轉成byte[]
        byte[] textBytes = mNote.getText().toString().getBytes();
        // 2.將 byte[] 置入 Ndef 記錄列
        NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
                "text/plain".getBytes(),
                new byte[] {}, textBytes);
        // 3.包裝成 NdefMessage 回傳
        return new NdefMessage(new NdefRecord[] {
                textRecord
        });
    }

    // 寫入標簽: (欲寫入之記錄 [NdefMessage format], 標簽資訊)
    // 注意:要區分格式化前與格式化後的寫入邏輯
    boolean writeTag(NdefMessage message, Tag tag) {
        // 1.訊息長度大小
        int size = message.toByteArray().length;
        try {
            // 2.取得 Ndef 物件
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) { // 此標簽已格式化

                // 連接開啟I/O通道
                ndef.connect();

                if (!ndef.isWritable()) { // 判斷此卡是否是唯讀 ?
                    Toast.makeText(context, "此標簽 read-only.",
                            Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return false;
                }
                if (ndef.getMaxSize() < size) { // 判斷此卡是否有足夠空間寫入資訊 ?
                    Toast.makeText(context, "標簽容量不足\n標簽容量為: " +
                            ndef.getMaxSize() + " bytes\n寫入訊息容量為: " +
                            size + " bytes.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return false;
                }
                // 寫入
                ndef.writeNdefMessage(message);
                Toast.makeText(context, "ＮＦＣ標簽資訊寫入成功(pre-formatted)",
                        Toast.LENGTH_SHORT).show();
                dialog.cancel();
                dialog.dismiss();
                writeMode = false;
                return true;
            } else { // 此標簽未格式化
                // 格式化標籤
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        // 連接開啟I/O通道
                        format.connect();
                        // format完成後寫入資訊
                        format.format(message);
                        Toast.makeText(context, "格式化成功並ＮＦＣ標簽資訊寫入成功",
                                Toast.LENGTH_SHORT).show();
                        dialog.cancel();
                        dialog.dismiss();
                        return true;
                    } catch (IOException e) {
                        Toast.makeText(context, "格式化失敗", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        return false;
                    }
                } else {
                    Toast.makeText(context, "此ＮＦＣ標簽並不支援NDEF讀寫格式.",
                            Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return false;
                }
            }
        } catch (Exception e) {

            if(dialog != null) {
                Toast.makeText(context, "寫入失敗", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        }

        return false;
    }
}
