package com.safestream.ai
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter
import android.os.Bundle; import android.os.Handler; import android.os.Looper; import android.provider.Settings; import android.view.LayoutInflater; import android.view.View; import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager; import android.widget.*
import androidx.appcompat.app.AlertDialog; import androidx.appcompat.app.AppCompatActivity; import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager; import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus:TextView; private lateinit var tvLast:TextView; private lateinit var tvCount:TextView
    private lateinit var tvTime:TextView; private lateinit var tvThreshVal:TextView; private lateinit var tvThreshLbl:TextView
    private lateinit var dot:View; private lateinit var btnToggle:MaterialButton; private lateinit var btnSettings:MaterialButton
    private lateinit var btnClear:MaterialButton; private lateinit var seek:SeekBar; private lateinit var rv:RecyclerView; private lateinit var tvEmpty:TextView
    private val prefs by lazy { getSharedPreferences("safestream_prefs",Context.MODE_PRIVATE) }
    private val handler=Handler(Looper.getMainLooper())
    private val timeRunner=object:Runnable{override fun run(){refreshTime();handler.postDelayed(this,60_000)}}
    private val receiver=object:BroadcastReceiver(){
        override fun onReceive(ctx:Context,intent:Intent){
            when(intent.action){
                "com.safestream.STATUS"->updateStatus(intent.getStringExtra("status")=="RUNNING")
                "com.safestream.BLOCK"->{refreshLog();tvLast.text="Last: ${intent.getStringExtra("title")?:""}"}
            }
        }
    }
    override fun onCreate(s:Bundle?){
        super.onCreate(s);setContentView(R.layout.activity_main);setSupportActionBar(findViewById(R.id.toolbar))
        tvStatus=findViewById(R.id.tv_service_status);tvLast=findViewById(R.id.tv_last_blocked);tvCount=findViewById(R.id.tv_block_count)
        tvTime=findViewById(R.id.tv_screen_time);tvThreshVal=findViewById(R.id.tv_threshold_value);tvThreshLbl=findViewById(R.id.tv_threshold_label)
        dot=findViewById(R.id.view_status_dot);btnToggle=findViewById(R.id.btn_toggle_service);btnSettings=findViewById(R.id.btn_settings)
        btnClear=findViewById(R.id.btn_clear_log);seek=findViewById(R.id.seek_threshold);rv=findViewById(R.id.rv_block_log);tvEmpty=findViewById(R.id.tv_empty_log)
        rv.layoutManager=LinearLayoutManager(this)
        seek.progress=prefs.getInt("threshold",75);applyLabel(seek.progress)
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(sb:SeekBar,v:Int,f:Boolean){applyLabel(v)}
            override fun onStartTrackingTouch(sb:SeekBar){}
            override fun onStopTrackingTouch(sb:SeekBar){prefs.edit().putInt("threshold",sb.progress).apply();Toast.makeText(this@MainActivity,getString(R.string.toast_threshold_updated,sb.progress),Toast.LENGTH_SHORT).show()}
        })
        btnToggle.setOnClickListener{if(isEnabled())showDisable() else showEnable()}
        btnSettings.setOnClickListener{startActivity(Intent(this,SettingsActivity::class.java))}
        btnClear.setOnClickListener{BlockEventLogger.clear(this);refreshLog();Toast.makeText(this,getString(R.string.toast_log_cleared),Toast.LENGTH_SHORT).show()}
        refreshLog();refreshTime();updateStatus(isEnabled())
        val f=IntentFilter().apply{addAction("com.safestream.STATUS");addAction("com.safestream.BLOCK")}
        ContextCompat.registerReceiver(this,receiver,f,ContextCompat.RECEIVER_NOT_EXPORTED)
        if(!isEnabled()) showEnable()
        if(!ScreenTimeTracker.hasPermission(this)) AlertDialog.Builder(this).setTitle("Screen Time").setMessage("Enable Usage Access for SafeStream in Settings.").setPositiveButton("Open"){_,_->ScreenTimeTracker.openPermissionSettings(this)}.setNegativeButton("Skip",null).show()
    }
    override fun onResume(){super.onResume();updateStatus(isEnabled());refreshLog();handler.post(timeRunner)}
    override fun onPause(){super.onPause();handler.removeCallbacks(timeRunner)}
    override fun onDestroy(){super.onDestroy();try{unregisterReceiver(receiver)}catch(_:Exception){}}
    private fun applyLabel(v:Int){tvThreshVal.text="$v";tvThreshLbl.text=when{v>=85->"Strict";v>=65->"Balanced";else->"Relaxed"}}
    private fun refreshLog(){val ev=BlockEventLogger.getAll(this);tvCount.text="${ev.size}";if(ev.isEmpty()){rv.visibility=View.GONE;tvEmpty.visibility=View.VISIBLE}else{rv.visibility=View.VISIBLE;tvEmpty.visibility=View.GONE;rv.adapter=BlockLogAdapter(ev)}}
    private fun refreshTime(){val m=ScreenTimeTracker.getYouTubeMinutesToday(this);val limit=prefs.getInt("time_limit_min",60);tvTime.text=ScreenTimeTracker.formatMinutes(m);tvTime.setTextColor(ContextCompat.getColor(this,if(m>=0&&m>=limit)R.color.danger_red else R.color.amber))}
    private fun updateStatus(running:Boolean){tvStatus.text=getString(if(running)R.string.status_active else R.string.status_inactive);tvStatus.setTextColor(ContextCompat.getColor(this,if(running)R.color.safe_green else R.color.danger_red));dot.setBackgroundResource(if(running)R.drawable.circle_dot_green else R.drawable.circle_dot_red);btnToggle.text=if(running)"Disable" else "Enable"}
    private fun isEnabled():Boolean{val am=getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager;return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{it.resolveInfo.serviceInfo.packageName==packageName&&it.resolveInfo.serviceInfo.name==SafeAccessibilityService::class.java.name}}
    private fun showEnable(){AlertDialog.Builder(this).setTitle(getString(R.string.dialog_enable_title)).setMessage("1. Tap SafeStream AI\n2. Toggle ON\n3. Tap Allow").setPositiveButton("Open Settings"){_,_->startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}.setNegativeButton("Not Now",null).show()}
    private fun showDisable(){AlertDialog.Builder(this).setTitle(getString(R.string.dialog_disable_title)).setMessage(getString(R.string.dialog_disable_message)).setPositiveButton("Open Settings"){_,_->startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}.setNegativeButton("Cancel",null).show()}
}
class BlockLogAdapter(private val ev:List<Map<String,Any>>):RecyclerView.Adapter<BlockLogAdapter.VH>(){
    class VH(v:View):RecyclerView.ViewHolder(v){val title:TextView=v.findViewById(R.id.tv_log_title);val channel:TextView=v.findViewById(R.id.tv_log_channel);val score:TextView=v.findViewById(R.id.tv_log_score);val time:TextView=v.findViewById(R.id.tv_log_time);val reason:TextView=v.findViewById(R.id.tv_log_reason)}
    override fun onCreateViewHolder(p:ViewGroup,t:Int)=VH(LayoutInflater.from(p.context).inflate(R.layout.item_block_log,p,false))
    override fun getItemCount()=ev.size
    override fun onBindViewHolder(h:VH,i:Int){val e=ev[i];h.title.text=e["title"] as? String?:"";h.channel.text=e["channel"] as? String?:"";h.score.text="Score: ${e["score"]}";h.time.text=e["time_str"] as? String?:"";h.reason.text=e["reason"] as? String?:""}
}
