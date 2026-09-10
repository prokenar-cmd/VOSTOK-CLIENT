package com.blackrussia.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.blackrussia.game.R;
import com.blackrussia.launcher.account.AccountApi;

import java.util.ArrayList;
import java.util.List;

public class VostokLauncherView extends View {

    public enum Action { PLAY, SERVER, ACCOUNT, NEWS, SITE, VK, TELEGRAM, SUPPORT, SETTINGS }
    public interface ActionListener { void onLauncherAction(Action action); }
    public interface CharacterSelectionListener { void onCharacterSelected(long characterId); }

    private static final RectF PLAY = new RectF(1380, 620, 1792, 724);
    private static final RectF SERVER = new RectF(994, 625, 1369, 714);
    private static final RectF ACCOUNT = new RectF(34, 628, 405, 721);
    private static final RectF NEWS = new RectF(34, 205, 469, 610);
    private static final RectF SITE = new RectF(1062, 38, 1185, 94);
    private static final RectF VK = new RectF(1185, 38, 1327, 94);
    private static final RectF TELEGRAM = new RectF(1327, 38, 1461, 94);
    private static final RectF SUPPORT = new RectF(1461, 38, 1619, 94);
    private static final RectF SETTINGS = new RectF(1619, 38, 1790, 94);
    private static final float CHARACTER_ROW_HEIGHT = 67.0f;
    private static final float CHARACTER_PANEL_GAP = 10.0f;
    private static final int CHARACTER_PANEL_MAX_VISIBLE = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<AccountApi.CharacterInfo> characters = new ArrayList<>();
    private Bitmap launcherBitmap;
    private ActionListener listener;
    private CharacterSelectionListener characterSelectionListener;
    private Typeface uiRegular = Typeface.DEFAULT;
    private Typeface uiBold = Typeface.DEFAULT_BOLD;
    private float scale = 1.0f, drawLeft = 0.0f, drawTop = 0.0f;
    private boolean accountAuthenticated = false;
    private long accountId = 0L, selectedCharacterId = 0L;
    private String selectedCharacterName = "";
    private boolean characterPanelOpen = false;
    private float characterPanelProgress = 0.0f;
    private ValueAnimator characterPanelAnimator;

    public VostokLauncherView(Context context) { super(context); init(); }
    public VostokLauncherView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public VostokLauncherView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setClickable(true); setFocusable(true);
        launcherBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.vostok_launcher_reference);
        Typeface regular = ResourcesCompat.getFont(getContext(), R.font.pt_root_ui_regular);
        Typeface bold = ResourcesCompat.getFont(getContext(), R.font.pt_root_ui_bold);
        if (regular != null) uiRegular = regular;
        if (bold != null) uiBold = bold;
    }

    public void setActionListener(ActionListener listener) { this.listener = listener; }
    public void setCharacterSelectionListener(CharacterSelectionListener listener) { this.characterSelectionListener = listener; }

    public void setAccountState(boolean authenticated, long newAccountId, long newCharacterId, String newCharacterName) {
        accountAuthenticated = authenticated; accountId = newAccountId; selectedCharacterId = newCharacterId;
        selectedCharacterName = newCharacterName == null ? "" : newCharacterName;
        characters.clear(); closeCharacterPanel(false); invalidate();
    }

    public void setAccountState(boolean authenticated, long newAccountId, List<AccountApi.CharacterInfo> newCharacters, long newSelectedCharacterId) {
        accountAuthenticated = authenticated; accountId = newAccountId; characters.clear();
        if (newCharacters != null) characters.addAll(newCharacters);
        AccountApi.CharacterInfo selected = findCharacter(newSelectedCharacterId);
        if (selected == null && !characters.isEmpty()) selected = characters.get(0);
        selectedCharacterId = selected == null ? 0L : selected.id;
        selectedCharacterName = selected == null ? "" : selected.name;
        if (!authenticated || characters.isEmpty()) closeCharacterPanel(false);
        invalidate();
    }

    public void setSelectedCharacter(long characterId) {
        AccountApi.CharacterInfo selected = findCharacter(characterId); if (selected == null) return;
        selectedCharacterId = selected.id; selectedCharacterName = selected.name; closeCharacterPanel(true); invalidate();
    }
    public boolean isCharacterPanelOpen() { return characterPanelOpen || characterPanelProgress > 0.01f; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (launcherBitmap == null || launcherBitmap.isRecycled()) return;
        float bitmapW = launcherBitmap.getWidth(), bitmapH = launcherBitmap.getHeight();
        if (bitmapW <= 0 || bitmapH <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        scale = Math.max(getWidth() / bitmapW, getHeight() / bitmapH);
        float drawW = bitmapW * scale, drawH = bitmapH * scale;
        drawLeft = (getWidth() - drawW) * 0.5f; drawTop = (getHeight() - drawH) * 0.5f;
        RectF dst = new RectF(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH);
        canvas.drawBitmap(launcherBitmap, null, dst, paint);
        canvas.save(); canvas.translate(drawLeft, drawTop); canvas.scale(scale, scale);
        drawCharacterCard(canvas); drawCharacterPanel(canvas); canvas.restore();
    }

    private void drawCharacterCard(Canvas canvas) {
        overlayPaint.setStyle(Paint.Style.FILL); overlayPaint.setColor(Color.argb(239, 22, 20, 20));
        canvas.drawRoundRect(ACCOUNT, 24.0f, 24.0f, overlayPaint);
        overlayPaint.setStyle(Paint.Style.STROKE); overlayPaint.setStrokeWidth(1.2f); overlayPaint.setColor(Color.argb(85,255,255,255));
        canvas.drawRoundRect(ACCOUNT,24.0f,24.0f,overlayPaint); overlayPaint.setStyle(Paint.Style.FILL);
        float avatarX=84.0f, avatarY=674.0f;
        overlayPaint.setColor(accountAuthenticated?Color.rgb(255,112,42):Color.rgb(78,76,76)); canvas.drawCircle(avatarX,avatarY,35.0f,overlayPaint);
        overlayPaint.setColor(Color.rgb(30,29,29)); canvas.drawCircle(avatarX,avatarY,31.5f,overlayPaint);
        overlayPaint.setTextAlign(Paint.Align.CENTER); overlayPaint.setTypeface(uiBold); overlayPaint.setTextSize(25.0f); overlayPaint.setColor(Color.WHITE);
        String initial=accountAuthenticated&&!selectedCharacterName.isEmpty()?selectedCharacterName.substring(0,1).toUpperCase():"•";
        float initialBase=avatarY-(overlayPaint.ascent()+overlayPaint.descent())*0.5f; canvas.drawText(initial,avatarX,initialBase,overlayPaint);
        overlayPaint.setTextAlign(Paint.Align.LEFT); overlayPaint.setTypeface(uiBold); overlayPaint.setTextSize(21.0f); overlayPaint.setColor(Color.WHITE);
        String title=!accountAuthenticated?"НЕ АВТОРИЗОВАН":selectedCharacterName.isEmpty()?"НЕТ ПЕРСОНАЖА":selectedCharacterName.toUpperCase();
        canvas.drawText(title,146.0f,671.0f,overlayPaint);
        overlayPaint.setTypeface(uiRegular); overlayPaint.setTextSize(16.0f); overlayPaint.setColor(Color.argb(205,200,196,192));
        String subtitle=!accountAuthenticated?"Нажмите ИГРАТЬ для входа":selectedCharacterId>0?"ID: "+selectedCharacterId:"Создайте первого персонажа";
        canvas.drawText(subtitle,146.0f,697.0f,overlayPaint);
        RectF control=new RectF(335,648,387,704); overlayPaint.setColor(Color.argb(190,53,49,48)); canvas.drawRoundRect(control,14,14,overlayPaint);
        overlayPaint.setStyle(Paint.Style.STROKE); overlayPaint.setStrokeWidth(3); overlayPaint.setStrokeCap(Paint.Cap.ROUND); overlayPaint.setColor(Color.argb(235,235,230,226));
        if(accountAuthenticated&&!characters.isEmpty()) { float cy=control.centerY(),cx=control.centerX(); if(characterPanelProgress>0.5f){canvas.drawLine(cx-8,cy+4,cx,cy-4,overlayPaint);canvas.drawLine(cx,cy-4,cx+8,cy+4,overlayPaint);}else{canvas.drawLine(cx-8,cy-4,cx,cy+4,overlayPaint);canvas.drawLine(cx,cy+4,cx+8,cy-4,overlayPaint);} } else canvas.drawCircle(control.centerX(),control.centerY(),4,overlayPaint);
        overlayPaint.setStrokeCap(Paint.Cap.BUTT); overlayPaint.setStyle(Paint.Style.FILL);
    }

    private void drawCharacterPanel(Canvas canvas) {
        if(characterPanelProgress<=0.001f||characters.isEmpty()) return;
        int visibleCount=Math.min(characters.size(),CHARACTER_PANEL_MAX_VISIBLE); float panelHeight=visibleCount*CHARACTER_ROW_HEIGHT+18;
        float panelBottom=ACCOUNT.top-CHARACTER_PANEL_GAP,panelTop=panelBottom-panelHeight; RectF panel=new RectF(34,panelTop,458,panelBottom);
        float translateY=(1-characterPanelProgress)*(panelHeight+CHARACTER_PANEL_GAP); int alpha=Math.max(0,Math.min(255,(int)(characterPanelProgress*255)));
        canvas.save(); canvas.clipRect(0,180,520,ACCOUNT.top-2); canvas.translate(0,translateY);
        overlayPaint.setStyle(Paint.Style.FILL); overlayPaint.setColor(Color.argb((int)(238*alpha/255f),24,21,20)); canvas.drawRoundRect(panel,22,22,overlayPaint);
        overlayPaint.setStyle(Paint.Style.STROKE);overlayPaint.setStrokeWidth(1.2f);overlayPaint.setColor(Color.argb((int)(105*alpha/255f),255,154,105));canvas.drawRoundRect(panel,22,22,overlayPaint);overlayPaint.setStyle(Paint.Style.FILL);
        float rowTop=panelTop+9;
        for(int i=0;i<visibleCount;i++){
            AccountApi.CharacterInfo c=characters.get(i); RectF row=new RectF(panel.left+9,rowTop,panel.right-9,rowTop+CHARACTER_ROW_HEIGHT-5); boolean selected=c.id==selectedCharacterId;
            overlayPaint.setColor(selected?Color.argb((int)(190*alpha/255f),104,46,21):Color.argb((int)(185*alpha/255f),42,38,36));canvas.drawRoundRect(row,15,15,overlayPaint);
            if(selected){overlayPaint.setStyle(Paint.Style.STROKE);overlayPaint.setStrokeWidth(1.8f);overlayPaint.setColor(Color.argb(alpha,255,111,39));canvas.drawRoundRect(row,15,15,overlayPaint);overlayPaint.setStyle(Paint.Style.FILL);}
            float ax=row.left+42,ay=row.centerY();overlayPaint.setColor(selected?Color.argb(alpha,255,111,39):Color.argb(alpha,106,101,98));canvas.drawCircle(ax,ay,25,overlayPaint);overlayPaint.setColor(Color.argb(alpha,31,29,29));canvas.drawCircle(ax,ay,22,overlayPaint);
            overlayPaint.setTextAlign(Paint.Align.CENTER);overlayPaint.setTypeface(uiBold);overlayPaint.setTextSize(18);overlayPaint.setColor(Color.argb(alpha,255,255,255));String ini=c.name.isEmpty()?"?":c.name.substring(0,1).toUpperCase();float base=ay-(overlayPaint.ascent()+overlayPaint.descent())*.5f;canvas.drawText(ini,ax,base,overlayPaint);
            overlayPaint.setTextAlign(Paint.Align.LEFT);overlayPaint.setTypeface(uiBold);overlayPaint.setTextSize(19);canvas.drawText(c.name.toUpperCase(),row.left+82,row.top+25,overlayPaint);overlayPaint.setTypeface(uiRegular);overlayPaint.setTextSize(14);overlayPaint.setColor(Color.argb((int)(210*alpha/255f),206,201,198));canvas.drawText("ID: "+c.id+"   •   LVL "+c.level,row.left+82,row.top+47,overlayPaint);
            overlayPaint.setStyle(Paint.Style.STROKE);overlayPaint.setStrokeWidth(2);overlayPaint.setColor(selected?Color.argb(alpha,255,111,39):Color.argb((int)(165*alpha/255f),200,195,191));canvas.drawCircle(row.right-28,row.centerY(),10,overlayPaint);if(selected){overlayPaint.setStyle(Paint.Style.FILL);canvas.drawCircle(row.right-28,row.centerY(),5,overlayPaint);}overlayPaint.setStyle(Paint.Style.FILL);
            rowTop+=CHARACTER_ROW_HEIGHT;
        }
        canvas.restore();
    }

    private void toggleCharacterPanel(){if(!accountAuthenticated||characters.isEmpty())return;if(characterPanelOpen)closeCharacterPanel(true);else openCharacterPanel();}
    private void openCharacterPanel(){characterPanelOpen=true;animateCharacterPanelTo(1f);}
    private void closeCharacterPanel(boolean animate){characterPanelOpen=false;if(characterPanelAnimator!=null)characterPanelAnimator.cancel();if(!animate){characterPanelProgress=0;invalidate();return;}animateCharacterPanelTo(0);}
    private void animateCharacterPanelTo(float target){if(characterPanelAnimator!=null)characterPanelAnimator.cancel();characterPanelAnimator=ValueAnimator.ofFloat(characterPanelProgress,target);characterPanelAnimator.setDuration(220);characterPanelAnimator.setInterpolator(new DecelerateInterpolator());characterPanelAnimator.addUpdateListener(a->{characterPanelProgress=(Float)a.getAnimatedValue();invalidate();});characterPanelAnimator.start();}

    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()!=MotionEvent.ACTION_UP)return true; float x=(event.getX()-drawLeft)/scale,y=(event.getY()-drawTop)/scale;
        if(characterPanelProgress>0.85f&&!characters.isEmpty()){long id=hitCharacterPanel(x,y);if(id>0){setSelectedCharacter(id);if(characterSelectionListener!=null)characterSelectionListener.onCharacterSelected(id);performClick();return true;}}
        if(ACCOUNT.contains(x,y)){performClick();if(accountAuthenticated&&!characters.isEmpty())toggleCharacterPanel();else if(listener!=null)listener.onLauncherAction(Action.ACCOUNT);return true;}
        if(isCharacterPanelOpen())closeCharacterPanel(true);Action action=hitTest(x,y);if(action!=null&&listener!=null){performClick();listener.onLauncherAction(action);}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private long hitCharacterPanel(float x,float y){int n=Math.min(characters.size(),CHARACTER_PANEL_MAX_VISIBLE);float ph=n*CHARACTER_ROW_HEIGHT+18,pb=ACCOUNT.top-CHARACTER_PANEL_GAP,pt=pb-ph;RectF p=new RectF(34,pt,458,pb);if(!p.contains(x,y))return 0;int idx=(int)((y-(pt+9))/CHARACTER_ROW_HEIGHT);return idx>=0&&idx<n?characters.get(idx).id:0;}
    private Action hitTest(float x,float y){if(PLAY.contains(x,y))return Action.PLAY;if(SERVER.contains(x,y))return Action.SERVER;if(SITE.contains(x,y))return Action.SITE;if(VK.contains(x,y))return Action.VK;if(TELEGRAM.contains(x,y))return Action.TELEGRAM;if(SUPPORT.contains(x,y))return Action.SUPPORT;if(SETTINGS.contains(x,y))return Action.SETTINGS;if(NEWS.contains(x,y))return Action.NEWS;return null;}
    private AccountApi.CharacterInfo findCharacter(long id){if(id<=0)return null;for(AccountApi.CharacterInfo c:characters)if(c.id==id)return c;return null;}
}
