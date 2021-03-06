
package com.brian.codeblog.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

import com.brian.codeblog.Config;
import com.brian.codeblog.adapter.MainTabAdapter;
import com.brian.codeblog.datacenter.preference.CommonPreference;
import com.brian.codeblog.datacenter.preference.SettingPreference;
import com.brian.codeblog.manager.PushManager;
import com.brian.codeblog.manager.ShareManager;
import com.brian.codeblog.manager.UpdateManager;
import com.brian.codeblog.manager.UsageStatsManager;
import com.brian.codeblog.util.LogUtil;
import com.brian.codeblog.util.TimeUtil;
import com.brian.codeblog.util.ToastUtil;
import com.brian.common.view.DrawerArrowDrawable;
import com.brian.csdnblog.R;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.OnCloseListener;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.OnOpenListener;
import com.jeremyfeinstein.slidingmenu.lib.app.SlidingFragmentActivity;
import com.umeng.analytics.MobclickAgent;
import com.umeng.onlineconfig.OnlineConfigAgent;
import com.umeng.onlineconfig.OnlineConfigLog;
import com.umeng.onlineconfig.UmengOnlineConfigureListener;

import org.json.JSONObject;

import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import tj.zl.op.normal.spot.SpotManager;

/**
 * 主界面
 */
public class MainTabActivity extends SlidingFragmentActivity {

    private static final String TAG = MainTabActivity.class.getSimpleName();

    @BindView(R.id.left_menu) ImageView mBtnMenu;
    @BindView(R.id.right_search) ImageView mBtnSearch;
    @BindView(R.id.tabs) TabLayout mTabLayout;
    // 主界面的页面切换
    @BindView(R.id.pager) ViewPager mViewpager = null;

    private DrawerArrowDrawable mArrowDrawable;

    private MainTabAdapter mTabAdapter = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setSwipeBackEnable(false); // 不可滑动返回，否则容易异常退出
        setContentView(R.layout.activity_main_tab);
        ButterKnife.bind(this);

        MobclickAgent.enableEncrypt(true);

        initUI();
        initListener();
        recoveryUI(); // 恢复上次浏览视图：主要是tab位置
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // 延迟初始化非必要组件
        getUIHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // 初始化更新模块
                UpdateManager.getInstance().initUpdate();
                initOnlineParams();
            }
        }, 3000);
    }

    private void initUI() {
        mTabAdapter = new MainTabAdapter(getSupportFragmentManager());
        // 视图切换器
        mViewpager.setOffscreenPageLimit(1);// 预先加载页面的数量
        mViewpager.setAdapter(mTabAdapter);
        mTabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);//设置滑动模式
        mTabLayout.setupWithViewPager(mViewpager);

        // 初始化侧滑栏
        initSlidingMenu();
    }

    private void recoveryUI() {
        int initPosition = mTabAdapter.getCount()/2;

        if (CommonPreference.getInstance().isNeedRecoveryLastStatus()) {
            // 读取上次退出时停留的页面序号
            initPosition = CommonPreference.getInstance().getIndicatorPosition(initPosition);
        }
        mViewpager.setCurrentItem(initPosition, false);

        UsageStatsManager.sendUsageData(UsageStatsManager.USAGE_MAIN_TAB, mTabAdapter.getPageTitle(initPosition));
    }

    private void initSlidingMenu() {
        // 设置左侧滑动菜单
        setBehindContentView(R.layout.menu_frame_left);
        getSupportFragmentManager().beginTransaction().replace(R.id.menu_frame, new SidePageFragment()).commit();

        // 实例化滑动菜单对象
        SlidingMenu sm = getSlidingMenu();
        sm.setMode(SlidingMenu.LEFT);// 设置可以左右滑动菜单
        sm.setShadowWidthRes(R.dimen.shadow_width);// 设置滑动阴影的宽度
        sm.setShadowDrawable(null);// 设置滑动菜单阴影的图像资源
        sm.setBehindOffsetRes(R.dimen.slidingmenu_offset);// 设置左侧栏展开时与右边框的margin
        sm.setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);// 设置触摸屏幕的模式，从边上滑动才有效
        sm.setFadeDegree(0.8f);// 设置渐入渐出效果的值,1.0为全黑
        sm.setBehindScrollScale(0.5f);// 设置下方视图的在滚动时的缩放比例，1.0为从左往右推过来（无覆盖效果）
        sm.setOnOpenListener(new OnOpenListener() {
            @Override
            public void onOpen() {
                LogUtil.i(TAG, "OnOpenListener");

                UsageStatsManager.sendUsageData(UsageStatsManager.USAGE_SLIDEMENU_SHOW);
            }
        });

        sm.setOnCloseListener(new OnCloseListener() {
            @Override
            public void onClose() {
                LogUtil.i(TAG, "onClose");
            }
        });

        mArrowDrawable = new DrawerArrowDrawable(this);
        mArrowDrawable.setColor(getResources().getColor(R.color.white));
        mBtnMenu.setImageDrawable(mArrowDrawable);
        sm.setOnScrollListener(new SlidingMenu.OnScrollListener() {

            @Override
            public void onScroll(float percentOpen) {
                mArrowDrawable.setProgress(percentOpen);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        ShareManager.getInstance().onActivityResult(requestCode, resultCode, data);
    }

    private void initListener() {
        mBtnMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getSlidingMenu().toggle(true);
            }
        });
        mBtnSearch.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SearchActivity.startActivity(MainTabActivity.this);

                UsageStatsManager.sendUsageData(UsageStatsManager.USAGE_SEARCH);
            }
        });

        mViewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }
            @Override
            public void onPageSelected(int position) {
                UsageStatsManager.sendUsageData(UsageStatsManager.USAGE_MAIN_TAB, mTabAdapter.getPageTitle(position));
            }
            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {

            if (getSlidingMenu().isMenuShowing()) {// 左侧栏已展开
                getSlidingMenu().toggle(true);
            } else {

                boolean isRunInBack = SettingPreference.getInstance().getRunInBackEnable();
                if (isRunInBack) {
                    moveTaskToBack(true);
                } else {
                    finish();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            getSlidingMenu().toggle(true);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        PushManager.getInstance().handlePushMessageIfNeed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd(this.getClass().getName());
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onStop() {
        CommonPreference.getInstance().setIndicatorPosition(mViewpager.getCurrentItem());
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        int count = CommonPreference.getInstance().getBlogReadCount();
        String time = TimeUtil.convCountTime(CommonPreference.getInstance().getBlogReadTime());
        ToastUtil.showMsgS(String.format(Locale.CHINA, "累计学习 %d篇博文，时长 %s", count, time));
        super.onDestroy();
        OnlineConfigAgent.getInstance().removeOnlineConfigListener();
        SpotManager.getInstance(this).onDestroy();
    }

    // 当启动模式为singletask，重新被启动时调用
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // 若参数中无
        int initPosition = CommonPreference.getInstance().getIndicatorPosition(mTabAdapter.getCount()/2);
        mViewpager.setCurrentItem(initPosition, false);

        getIntent().putExtras(intent);// 共享数据
    }

    private void initOnlineParams() {
        OnlineConfigAgent.getInstance().setDebugMode(Config.isDebug);
        OnlineConfigAgent.getInstance().updateOnlineConfig(this);

        OnlineConfigAgent.getInstance().setOnlineConfigListener(new UmengOnlineConfigureListener() {
            @Override
            public void onDataReceived(JSONObject json) {
                OnlineConfigLog.d("OnlineConfig", "json=" + json);
            }
        });
    }

}
