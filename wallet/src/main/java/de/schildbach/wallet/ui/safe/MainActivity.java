package de.schildbach.wallet.ui.safe;


import android.Manifest;
import android.app.*;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.*;
import android.support.annotation.NonNull;
import android.support.design.internal.BottomNavigationItemView;
import android.support.design.widget.BottomNavigationView;

import android.support.v13.app.ActivityCompat;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.common.base.Charsets;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.ui.*;
import de.schildbach.wallet.ui.InputParser.BinaryInputParser;
import de.schildbach.wallet.ui.safe.utils.CommonUtils;
import de.schildbach.wallet.ui.safe.utils.IMMLeaks;
import de.schildbach.wallet.ui.safe.utils.NoScrollViewPager;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.ui.send.SweepWalletActivity;
import de.schildbach.wallet.util.*;
import de.schildbach.wallet.R;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.VersionedChecksummedBytes;
import org.bitcoinj.wallet.Wallet;

import java.io.*;
import java.util.*;

/**
 * 首页
 * @author zhangmiao
 */
public final class MainActivity extends AbstractBindServiceActivity {

    private static final int DIALOG_BACKUP_WALLET_PERMISSION = 0;
    private static final int DIALOG_RESTORE_WALLET_PERMISSION = 1;
    private static final int DIALOG_RESTORE_WALLET = 2;
    private static final int DIALOG_TIMESKEW_ALERT = 3;
    private static final int DIALOG_VERSION_ALERT = 4;
    private static final int DIALOG_LOW_STORAGE_ALERT = 5;

    private static final int REQUEST_CODE_SCAN = 0;
    private static final int REQUEST_CODE_BACKUP_WALLET = 1;
    private static final int REQUEST_CODE_RESTORE_WALLET = 2;

    private MenuItem prevMenuItem;
    private NoScrollViewPager viewPager;
    private ViewPagerAdapter adapter;
    private BottomNavigationView navigationView;
    private View viewFakeForSafetySubmenu;

    private WalletApplication application;
    private Configuration config;
    private Wallet wallet;

    private Handler handler = new Handler();

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        application = getWalletApplication();
        config = application.getConfiguration();
        wallet = application.getWallet();

        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            IMMLeaks.fixFocusedViewLeak(getApplication());
        }

        checkAlerts();

        config.touchLastUsed();

        handleIntent(getIntent());

        MaybeMaintenanceFragment.add(getFragmentManager());

        initView();

        viewFakeForSafetySubmenu = findViewById(R.id.subMenu);
        viewFakeForSafetySubmenu.setVisibility(View.GONE);
        registerForContextMenu(viewFakeForSafetySubmenu);

        if(config.getMustDownloadBlock()){
            final DialogBuilder mBuilder = DialogBuilder.warn(this, R.string.sync_block_title);
            mBuilder.setMessage(getString(R.string.sync_block_message, application.getVersionName()));
            mBuilder.setPositiveButton(getText(R.string.button_ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    WalletApplication.getInstance().resetBlockchain();
                    Intent intent = new Intent(MainActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }
            });
            AlertDialog mDialog = mBuilder.create();
            mDialog.setCancelable(false);
            mDialog.setCanceledOnTouchOutside(false);
            try {
                mDialog.show();
            } catch (Exception e) {
            }
        }
    }

    private void initView() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        TextView tvTitle = (TextView) toolbar.findViewById(R.id.tv_title);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        tvTitle.setText(getString(R.string.wallet_title));
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);

        viewPager = (NoScrollViewPager) findViewById(R.id.viewPager);
        navigationView = (BottomNavigationView) findViewById(R.id.navigationView);
        navigationView.setLabelVisibilityMode(1);
        navigationView.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.navigation_home:
                                viewPager.setCurrentItem(0);
                                break;
                            case R.id.navigation_issue_asset:
                                viewPager.setCurrentItem(1);
                                break;
                            case R.id.navigation_forum:
                                viewPager.setCurrentItem(2);
                                break;
                            case R.id.navigation_mine:
                                viewPager.setCurrentItem(3);
                                break;
                        }
                        return false;
                    }
                });
        BottomNavigationItemView itemView = NavigationViewUtils.getBottomNavigationItemView(navigationView, 2);
        itemView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (CommonUtils.isRepeatClick()) {
                    Intent intent = new Intent(getBaseContext(), ForumActivity.class);
                    startActivity(intent);
                }
                return true;
            }
        });
        adapter = new ViewPagerAdapter(getFragmentManager());
        adapter.addFragment(new HomeListFragment());
        adapter.addFragment(new IssueEnterFragment());
        adapter.addFragment(new EmptyFragment());
        adapter.addFragment(new MineFragment());
        viewPager.setOffscreenPageLimit(4);
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (prevMenuItem != null) {
                    prevMenuItem.setChecked(false);
                } else {
                    navigationView.getMenu().getItem(0).setChecked(false);
                }
                navigationView.getMenu().getItem(position).setChecked(true);
                prevMenuItem = navigationView.getMenu().getItem(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // delayed start so that UI has enough time to initialize
                getWalletApplication().startBlockchainService(true);

            }
        }, 1000);

        checkLowStorageAlert();

    }

    private void checkLowStorageAlert() {
        final Intent stickyIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
        if (stickyIntent != null) {
            showLowStorageDialog();
        }
    }

    private void showLowStorageDialog() {
        DialogBuilder mBuilder = DialogBuilder.warn(this, R.string.wallet_low_storage_dialog_title);
        mBuilder.setMessage(R.string.wallet_low_storage_dialog_msg);
        mBuilder.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int id) {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                finish();
            }
        });
        mBuilder.setNegativeButton(R.string.button_dismiss, null);
        AlertDialog dialog = mBuilder.create();
        try {
            dialog.show();
        } catch (Exception e) {
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        try {
            String action = intent.getAction();
            if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
                final String inputType = intent.getType();
                final NdefMessage ndefMessage = (NdefMessage) intent
                        .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
                final byte[] input = Nfc.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

                new BinaryInputParser(inputType, input) {
                    @Override
                    protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                        cannotClassify(inputType);
                    }

                    @Override
                    protected void error(final int messageResId, final Object... messageArgs) {
                        dialog(MainActivity.this, null, 0, messageResId, messageArgs);
                    }
                }.parse();
            } else if ("to_home_page".equals(action)) {
                viewPager.setCurrentItem(0, false);
            } else if ("to_candy_record".equals(action)) {
                viewPager.setCurrentItem(3, false);
                Intent intent1 = new Intent(MainActivity.this, BaseWalletActivity.class);
                intent1.putExtra(BaseWalletActivity.CLASS, CandyRecordFragment.class);
                startActivity(intent1);
            } else if ("to_net_monitor".equals(action)) {
                viewPager.setCurrentItem(3, false);
                Intent intent2 = new Intent(MainActivity.this, NetworkMonitorActivity.class);
                startActivity(intent2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkAlerts() {
        final PackageInfo packageInfo = getWalletApplication().packageInfo();

        if (CrashReporter.hasSavedCrashTrace()) {
            final StringBuilder stackTrace = new StringBuilder();

            try {
                CrashReporter.appendSavedCrashTrace(stackTrace);
            } catch (final IOException x) {
                log.info("problem appending crash info", x);
            }

            final ReportIssueDialogBuilder dialog = new ReportIssueDialogBuilder(this,
                    R.string.report_issue_dialog_title_crash, R.string.report_issue_dialog_message_crash) {
                @Override
                protected CharSequence subject() {
                    return Constants.REPORT_SUBJECT_CRASH + " " + packageInfo.versionName;
                }

                @Override
                protected CharSequence collectApplicationInfo() throws IOException {
                    final StringBuilder applicationInfo = new StringBuilder();
                    CrashReporter.appendApplicationInfo(applicationInfo, application);
                    return applicationInfo;
                }

                @Override
                protected CharSequence collectStackTrace() throws IOException {
                    if (stackTrace.length() > 0)
                        return stackTrace;
                    else
                        return null;
                }

                @Override
                protected CharSequence collectDeviceInfo() throws IOException {
                    final StringBuilder deviceInfo = new StringBuilder();
                    CrashReporter.appendDeviceInfo(deviceInfo, MainActivity.this);
                    return deviceInfo;
                }

                @Override
                protected CharSequence collectWalletDump() {
                    return wallet.toString(false, true, true, null);
                }
            };

            dialog.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
                                           final int[] grantResults) {
        if (requestCode == REQUEST_CODE_BACKUP_WALLET) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                handleBackupWallet();
            else
                showDialog(DIALOG_BACKUP_WALLET_PERMISSION);
        } else if (requestCode == REQUEST_CODE_RESTORE_WALLET) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                handleRestoreWallet();
            else
                showDialog(DIALOG_RESTORE_WALLET_PERMISSION);
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK) {
            final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

            new InputParser.StringInputParser(input) {
                @Override
                protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                    SendCoinsActivity.start(MainActivity.this, paymentIntent);
                }

                @Override
                protected void handlePrivateKey(final VersionedChecksummedBytes key) {
                    SweepWalletActivity.start(MainActivity.this, key);
                }

                @Override
                protected void handleDirectTransaction(final Transaction tx) throws VerificationException {
                    application.processDirectTransaction(tx);
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    dialog(MainActivity.this, null, R.string.button_scan, messageResId, messageArgs);
                }
            }.parse();
        }
    }

    public void handleRequestCoins() {
        startActivity(new Intent(this, RequestCoinsActivity.class));
    }

    public void handleSendCoins() {
        startActivity(new Intent(this, SendCoinsActivity.class));
    }

    public void handleScan() {
        verifyPermissions(Manifest.permission.CAMERA, new PermissionCallBack() {
            @Override
            public void onGranted() {
                startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), REQUEST_CODE_SCAN);
            }

            @Override
            public void onDenied() {

            }
        });
    }

    public void handleBackupWallet() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            BackupWalletDialogFragment.show(getFragmentManager());
        else
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_BACKUP_WALLET);
    }

    public void handleRestoreWallet() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            showDialog(DIALOG_RESTORE_WALLET);
        else
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_RESTORE_WALLET);
    }

    public void handleBackupWalletToSeed() {
        BackupWalletToSeedDialogFragment.show(getFragmentManager());
    }

    public void handleRestoreWalletFromSeed() {
        showRestoreWalletFromSeedDialog();
    }

    public void handleEncryptKeys() {
        EncryptKeysDialogFragment.show(getFragmentManager());
    }

    @Override
    protected Dialog onCreateDialog(final int id, final Bundle args) {
        if (id == DIALOG_BACKUP_WALLET_PERMISSION)
            return createBackupWalletPermissionDialog();
        else if (id == DIALOG_RESTORE_WALLET_PERMISSION)
            return createRestoreWalletPermissionDialog();
        else if (id == DIALOG_RESTORE_WALLET)
            return createRestoreWalletDialog();
        else if (id == DIALOG_TIMESKEW_ALERT)
            return createTimeskewAlertDialog(args.getLong("diff_minutes"));
        else if (id == DIALOG_VERSION_ALERT)
            return createVersionAlertDialog();
        else if (id == DIALOG_LOW_STORAGE_ALERT)
            return createLowStorageAlertDialog();
        else
            throw new IllegalArgumentException();
    }

    @Override
    protected void onPrepareDialog(final int id, final Dialog dialog) {
        if (id == DIALOG_RESTORE_WALLET)
            prepareRestoreWalletDialog(dialog);
    }

    private Dialog createBackupWalletPermissionDialog() {
        final DialogBuilder dialog = new DialogBuilder(this);
        dialog.setTitle(R.string.backup_wallet_permission_dialog_title);
        dialog.setMessage(getString(R.string.backup_wallet_permission_dialog_message));
        dialog.singleDismissButton(null);
        return dialog.create();
    }

    private Dialog createRestoreWalletPermissionDialog() {
        final DialogBuilder dialog = new DialogBuilder(this);
        dialog.setTitle(R.string.restore_wallet_permission_dialog_title);
        dialog.setMessage(getString(R.string.restore_wallet_permission_dialog_message));
        dialog.singleDismissButton(null);
        return dialog.create();
    }

    private Dialog createRestoreWalletDialog() {
        final View view = getLayoutInflater().inflate(R.layout.restore_wallet_dialog, null);
        final TextView messageView = (TextView) view.findViewById(R.id.restore_wallet_dialog_message);
        final Spinner fileView = (Spinner) view.findViewById(R.id.import_keys_from_storage_file);
        final EditText passwordView = (EditText) view.findViewById(R.id.import_keys_from_storage_password);

        final DialogBuilder dialog = new DialogBuilder(this);
        dialog.setTitle(R.string.import_keys_dialog_title);
        dialog.setView(view);
        dialog.setPositiveButton(R.string.import_keys_dialog_button_import, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                final File file = (File) fileView.getSelectedItem();
                final String password = passwordView.getText().toString().trim();
                passwordView.setText(null); // get rid of it asap

                if (WalletUtils.BACKUP_FILE_FILTER.accept(file))
                    restoreWalletFromProtobuf(file);
                else if (WalletUtils.KEYS_FILE_FILTER.accept(file))
                    restorePrivateKeysFromBase58(file);
                else if (Crypto.OPENSSL_FILE_FILTER.accept(file))
                    restoreWalletFromEncrypted(file, password);
            }
        });
        dialog.setNegativeButton(R.string.button_cancel, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                passwordView.setText(null); // get rid of it asap
            }
        });
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(final DialogInterface dialog) {
                passwordView.setText(null); // get rid of it asap
            }
        });

        final FileAdapter adapter = new FileAdapter(this) {
            @Override
            public View getDropDownView(final int position, View row, final ViewGroup parent) {
                final File file = getItem(position);
                final boolean isExternal = Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.equals(file.getParentFile());
                final boolean isEncrypted = Crypto.OPENSSL_FILE_FILTER.accept(file);

                if (row == null)
                    row = inflater.inflate(R.layout.restore_wallet_file_row, null);

                final TextView filenameView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_filename);
                filenameView.setText(file.getName());

                final TextView securityView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_security);
                final String encryptedStr = context
                        .getString(isEncrypted ? R.string.import_keys_dialog_file_security_encrypted
                                : R.string.import_keys_dialog_file_security_unencrypted);
                final String storageStr = context
                        .getString(isExternal ? R.string.import_keys_dialog_file_security_external
                                : R.string.import_keys_dialog_file_security_internal);
                securityView.setText(encryptedStr + ", " + storageStr);

                final TextView createdView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_created);
                createdView.setText(context.getString(
                        isExternal ? R.string.import_keys_dialog_file_created_manual
                                : R.string.import_keys_dialog_file_created_automatic,
                        DateUtils.getRelativeTimeSpanString(context, file.lastModified(), true)));

                return row;
            }
        };

        final String path;
        final String backupPath = Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.getAbsolutePath();
        final String storagePath = Constants.Files.EXTERNAL_STORAGE_DIR.getAbsolutePath();
        if (backupPath.startsWith(storagePath))
            path = backupPath.substring(storagePath.length());
        else
            path = backupPath;
        messageView.setText(getString(R.string.import_keys_dialog_message, path));

        fileView.setAdapter(adapter);

        return dialog.create();
    }

    private void prepareRestoreWalletDialog(final Dialog dialog) {
        final AlertDialog alertDialog = (AlertDialog) dialog;

        final List<File> files = new LinkedList<File>();

        // external storage
        final File[] externalFiles = Constants.Files.EXTERNAL_WALLET_BACKUP_DIR.listFiles();
        if (externalFiles != null)
            for (final File file : externalFiles)
                if (Crypto.OPENSSL_FILE_FILTER.accept(file))
                    files.add(file);

        // internal storage
        for (final String filename : fileList())
            if (filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + '.'))
                files.add(new File(getFilesDir(), filename));

        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(final File lhs, final File rhs) {
                return lhs.getName().compareToIgnoreCase(rhs.getName());
            }
        });

        final View replaceWarningView = alertDialog
                .findViewById(R.id.restore_wallet_from_storage_dialog_replace_warning);
        final boolean hasCoins = wallet.getBalance(Wallet.BalanceType.ESTIMATED).signum() > 0;
        replaceWarningView.setVisibility(hasCoins ? View.VISIBLE : View.GONE);

        final Spinner fileView = (Spinner) alertDialog.findViewById(R.id.import_keys_from_storage_file);
        final FileAdapter adapter = (FileAdapter) fileView.getAdapter();
        adapter.setFiles(files);
        fileView.setEnabled(!adapter.isEmpty());

        final EditText passwordView = (EditText) alertDialog.findViewById(R.id.import_keys_from_storage_password);
        passwordView.setText(null);

        final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(
                passwordView, alertDialog) {
            @Override
            protected boolean hasFile() {
                return fileView.getSelectedItem() != null;
            }

            @Override
            protected boolean needsPassword() {
                final File selectedFile = (File) fileView.getSelectedItem();
                return selectedFile != null ? Crypto.OPENSSL_FILE_FILTER.accept(selectedFile) : false;
            }
        };
        passwordView.addTextChangedListener(dialogButtonEnabler);
        fileView.setOnItemSelectedListener(dialogButtonEnabler);

        final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.import_keys_from_storage_show);
        showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
    }

    private void showRestoreWalletFromSeedDialog() {
        RestoreWalletFromSeedDialogFragment.show(getFragmentManager());
    }

    private Dialog createLowStorageAlertDialog() {
        final DialogBuilder dialog = DialogBuilder.warn(this, R.string.wallet_low_storage_dialog_title);
        dialog.setMessage(R.string.wallet_low_storage_dialog_msg);
        dialog.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int id) {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                finish();
            }
        });
        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createTimeskewAlertDialog(final long diffMinutes) {
        final PackageManager pm = getPackageManager();
        final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_DATE_SETTINGS);

        final DialogBuilder dialog = DialogBuilder.warn(this, R.string.wallet_timeskew_dialog_title);
        dialog.setMessage(getString(R.string.wallet_timeskew_dialog_msg, diffMinutes));

        if (pm.resolveActivity(settingsIntent, 0) != null) {
            dialog.setPositiveButton(R.string.button_settings, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    startActivity(settingsIntent);
                    finish();
                }
            });
        }

        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private Dialog createVersionAlertDialog() {
        final PackageManager pm = getPackageManager();
        final Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(Constants.MARKET_APP_URL, getPackageName())));
        final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

        final DialogBuilder dialog = DialogBuilder.warn(this, R.string.wallet_version_dialog_title);
        final StringBuilder message = new StringBuilder(getString(R.string.wallet_version_dialog_msg));
        if (Build.VERSION.SDK_INT < Constants.SDK_DEPRECATED_BELOW)
            message.append("\n\n").append(getString(R.string.wallet_version_dialog_msg_deprecated));
        dialog.setMessage(message);

        if (pm.resolveActivity(marketIntent, 0) != null) {
            dialog.setPositiveButton(R.string.wallet_version_dialog_button_market,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int id) {
                            startActivity(marketIntent);
                            finish();
                        }
                    });
        }

        if (pm.resolveActivity(binaryIntent, 0) != null) {
            dialog.setNeutralButton(R.string.wallet_version_dialog_button_binary,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int id) {
                            startActivity(binaryIntent);
                            finish();
                        }
                    });
        }

        dialog.setNegativeButton(R.string.button_dismiss, null);
        return dialog.create();
    }

    private void restoreWalletFromEncrypted(final File file, final String password) {
        try {
            final BufferedReader cipherIn = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), Charsets.UTF_8));
            final StringBuilder cipherText = new StringBuilder();
            Io.copy(cipherIn, cipherText, Constants.BACKUP_MAX_CHARS);
            cipherIn.close();

            final byte[] plainText = Crypto.decryptBytes(cipherText.toString(), password.toCharArray());
            final InputStream is = new ByteArrayInputStream(plainText);

            restoreWallet(WalletUtils.restoreWalletFromProtobufOrBase58(is, Constants.NETWORK_PARAMETERS));

            log.info("successfully restored encrypted wallet: {}", file);
        } catch (final IOException x) {
            final DialogBuilder dialog = DialogBuilder.warn(this, R.string.import_export_keys_dialog_failure_title);
            dialog.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage()));
            dialog.setPositiveButton(R.string.button_dismiss, null);
            dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    showDialog(DIALOG_RESTORE_WALLET);
                }
            });
            dialog.show();

            log.info("problem restoring wallet: " + file, x);
        }
    }

    private void restoreWalletFromProtobuf(final File file) {
        FileInputStream is = null;
        try {
            is = new FileInputStream(file);
            restoreWallet(WalletUtils.restoreWalletFromProtobuf(is, Constants.NETWORK_PARAMETERS));

            log.info("successfully restored unencrypted wallet: {}", file);
        } catch (final IOException x) {
            final DialogBuilder dialog = DialogBuilder.warn(this, R.string.import_export_keys_dialog_failure_title);
            dialog.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage()));
            dialog.setPositiveButton(R.string.button_dismiss, null);
            dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    showDialog(DIALOG_RESTORE_WALLET);
                }
            });
            dialog.show();

            log.info("problem restoring unencrypted wallet: " + file, x);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (final IOException x2) {
                    // swallow
                }
            }
        }
    }

    private void restorePrivateKeysFromBase58(final File file) {
        FileInputStream is = null;
        try {
            is = new FileInputStream(file);
            restoreWallet(WalletUtils.restorePrivateKeysFromBase58(is, Constants.NETWORK_PARAMETERS));

            log.info("successfully restored unencrypted private keys: {}", file);
        } catch (final IOException x) {
            final DialogBuilder dialog = DialogBuilder.warn(this, R.string.import_export_keys_dialog_failure_title);
            dialog.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage()));
            dialog.setPositiveButton(R.string.button_dismiss, null);
            dialog.setNegativeButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    showDialog(DIALOG_RESTORE_WALLET);
                }
            });
            dialog.show();

            log.info("problem restoring private keys: " + file, x);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (final IOException e) {
                }
            }
        }
    }

    public void restoreWallet(final Wallet wallet) throws IOException {
        application.replaceWallet(wallet);

        config.disarmBackupReminder();

        final DialogBuilder dialog = new DialogBuilder(this);
        dialog.setTitle(R.string.restore_wallet_dialog_success);
        dialog.setMessage(getString(R.string.restore_wallet_dialog_success_replay));
        dialog.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int id) {
                getWalletApplication().resetBlockchain();
                Intent intent = new Intent(getApplication(), MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
        dialog.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.wallet_safety_options, menu);

        final String externalStorageState = Environment.getExternalStorageState();

        menu.findItem(R.id.wallet_options_restore_wallet).setEnabled(
                Environment.MEDIA_MOUNTED.equals(externalStorageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(externalStorageState));
        menu.findItem(R.id.wallet_options_backup_wallet).setEnabled(Environment.MEDIA_MOUNTED.equals(externalStorageState));
        menu.findItem(R.id.wallet_options_encrypt_keys).setTitle(
                wallet.isEncrypted() ? R.string.wallet_options_encrypt_keys_change : R.string.wallet_options_encrypt_keys_set);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.wallet_options_safety:
                HelpDialogFragment.page(getFragmentManager(), R.string.help_safety);
                return true;

            case R.id.wallet_options_backup_wallet:
                handleBackupWallet();
                return true;

            case R.id.wallet_options_restore_wallet:
                handleRestoreWallet();
                return true;

            case R.id.wallet_options_encrypt_keys:
                handleEncryptKeys();
                return true;
            case R.id.wallet_options_backup_wallet_to_seed:
                handleBackupWalletToSeed();
                return true;

            case R.id.wallet_options_restore_wallet_from_seed:
                handleRestoreWalletFromSeed();
                return true;
        }

        return super.onContextItemSelected(item);
    }

    public class ViewPagerAdapter extends FragmentPagerAdapter {

        public List<Fragment> mFragmentList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment) {
            mFragmentList.add(fragment);
        }

    }

    public void showContextMenu() {
        openContextMenu(viewFakeForSafetySubmenu);
    }


    @Override
    public void serviceBinded() {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WalletAddressFragment.flag = false;
        new Toast(this).cancleToast();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

}
