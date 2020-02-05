package com.example.musicplayer.ui.play

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.musicplayer.R
import com.example.musicplayer.data.DataHolder
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.ext.toTrack
import com.example.musicplayer.ui.songs.SongVM
import com.example.musicplayer.utils.Constants
import com.example.player.IPlayer
import com.example.player.Player
import com.iamsdt.androidextension.MyCoroutineContext
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_play.*
import kotlinx.android.synthetic.main.content_play.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.math.BigDecimal
import java.math.RoundingMode

class PlayActivity : AppCompatActivity() {

    private var title: String = ""
    private var type: String = ""
    private var id: Long = -10L
    private var songID: Long = -10L

    private var playImmediately = false

    private val vm: SongVM by viewModel()

    private val list: ArrayList<IPlayer.Track> = ArrayList()
    private var songsList: List<Song>? = ArrayList()

    private val mHandler: Handler = Handler()

    private val uiScope = MyCoroutineContext()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)
        setSupportActionBar(toolbar)

        Player.init(this)

        loadTypeData(intent)
        setUpActionbar(title)

        vm.getSongs(id, type).observe(this, Observer {
            preparePlayList(it)
        })

        Player.liveDataPlayNow.observe(this, Observer {
            drawUI(it)
        })

        Player.liveDataPlayerState.observe(this, Observer {
            togglePlayPauseIcon(it)
        })

        bindComponents()
        activateSeekabr()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        val newType = intent?.getStringExtra(Constants.Type.Type) ?: ""
        val newID = intent?.getLongExtra(Constants.Songs.ID, 0) ?: 0
        val newTitle = intent?.getStringExtra(Constants.Songs.Name) ?: ""
        val newPlayImmediately = intent?.getBooleanExtra("playlist", false) ?: false
        val newSongID = intent?.getLongExtra(Constants.Songs.SONG_ID, -10L) ?: 0

        if (id != newID) {
            id = newID
            vm.requestNewPLayList(id, newType)
        }

        if (newType != type) type = newType

        if (title != newTitle) {
            title = newTitle
            setUpActionbar(title)
        }

        if (songID != newSongID) {
            songID = newSongID
            playImmediately = newPlayImmediately
        }
    }

    private fun togglePlayPauseIcon(it: IPlayer.State?) {
        if (it == null) return

        when (it) {
            IPlayer.State.PLAY -> {
                play_pause.setImageDrawable(getDrawable(R.drawable.uamp_ic_pause_white_48dp))
                DataHolder.getInstance().isPlaying = true
            }

            IPlayer.State.PAUSE -> {
                play_pause.setImageDrawable(getDrawable(R.drawable.uamp_ic_play_arrow_white_48dp))
                DataHolder.getInstance().isPlaying = false
            }

            IPlayer.State.ERROR -> {
                play_pause.setImageDrawable(getDrawable(R.drawable.uamp_ic_play_arrow_white_48dp))
                DataHolder.getInstance().isPlaying = false
            }

            IPlayer.State.STOP -> {
                play_pause.setImageDrawable(getDrawable(R.drawable.uamp_ic_play_arrow_white_48dp))
                DataHolder.getInstance().isPlaying = false
            }

            IPlayer.State.PREPARING -> {
                play_pause.setImageDrawable(getDrawable(R.drawable.uamp_ic_play_arrow_white_48dp))
                DataHolder.getInstance().isPlaying = false
            }
        }

    }

    private fun bindComponents() {
        play_pause.setOnClickListener {
            Player.togglePlayPause()
        }

        next.setOnClickListener {
            Player.next()
        }

        prev.setOnClickListener {
            Player.prev()
        }
    }

    private fun setUpActionbar(title: String) {
        //set title
        toolbar.title = title
        //set action toolbar
        setSupportActionBar(toolbar)
    }

    private fun drawUI(it: IPlayer.Track?) {
        if (it == null) return

        Picasso.get()
            .load(it.imageUri)
            .placeholder(R.drawable.ic_audio_player)
            .error(R.drawable.ic_audio_player)
            .into(play_bcg)

        play_title.text = it.title
        play_artist.text = it.artist
        play_album.text = it.album

        play_endText.text = search(it.id.toLong())
        DataHolder.getInstance().currentTrack = it
    }

    private fun search(id: Long): String {
        val data = songsList?.filter {
            it.id == id
        }

        var status = "0:0"

        if (data?.isEmpty() != true) {
            DataHolder.getInstance().currentSong = data!![0]
            var finished = data[0].duration.toDouble()
            finished /= 1000.0
            finished /= 60.0
            val res = BigDecimal(finished).setScale(2, RoundingMode.HALF_EVEN)
            status = "$res"
        }

        return status
    }

    private fun preparePlayList(it: List<Song>?) {
        //save songs list
        songsList = it

        //clear all previous data
        list.clear()
        it?.forEach { list.add(it.toTrack()) }
        Player.playList = list

        if (playImmediately && list.isNotEmpty() && songID < 0) {
            playSong(list[0].id)
        }

        if (songID > 0) {
            if (!isAlreadyPlaying()) playSong(songID.toString())
        }
    }

    private fun isAlreadyPlaying(): Boolean {
        var status = false
        val holder = DataHolder.getInstance()
        if (holder.isPlaying && holder.currentTrack?.id == songID.toString()) {
            status = true
        }

        return status
    }

    private fun playSong(playID: String) {
        if (!DataHolder.getInstance().ifFirstTime) {
            uiScope.launch {
                delay(1000)
                Player.start(playID)
                play_endText.text = search(playID.toLong())
            }
            DataHolder.getInstance().ifFirstTime = true
        } else {
            Player.start(playID)
            play_endText.text = search(playID.toLong())
        }
    }

    private fun activateSeekabr() {
        runOnUiThread(object : Runnable {
            override fun run() {
                if (Player.trackDuration != 0L) {
                    val s = Player.currentPosition.toDouble()
                    val f = Player.trackDuration.toDouble()
                    val diff = (s / f) * 100
                    seekBar1.progress = diff.toInt()
                }

                var start = Player.currentPosition.toDouble()
                start /= 1000.0
                start /= 60.0
                val res = BigDecimal(start).setScale(2, RoundingMode.HALF_EVEN)
                play_startText.text = "$res"
                mHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun loadTypeData(intent: Intent) {
        type = intent.getStringExtra(Constants.Type.Type) ?: ""
        id = intent.getLongExtra(Constants.Songs.ID, 0)
        title = intent.getStringExtra(Constants.Songs.Name) ?: ""
        playImmediately = intent.getBooleanExtra("playlist", false)
        songID = intent.getLongExtra(Constants.Songs.SONG_ID, -10L)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

}
