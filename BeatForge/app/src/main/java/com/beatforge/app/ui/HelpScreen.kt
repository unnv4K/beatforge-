package com.beatforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatforge.app.BeatViewModel

private data class Topic(val title: String, val body: String, val mono: Boolean = false)
private data class Section(val title: String, val topics: List<Topic>)

private val GUIDE = listOf(
    Section("С ЧЕГО НАЧАТЬ", listOf(
        Topic("Как устроено приложение",
            "Внизу пять вкладок:\n" +
                "• Студия — 16 пэдов (барабанных кнопок), секвенсор и темп.\n" +
                "• Канал — настройки выбранного пэда: громкость, тон, загрузка и запись звука.\n" +
                "• FX — эффекты на весь бит, SP-404 эффекты и экспорт.\n" +
                "• Нарезка — режем длинный файл на 16 кусков.\n" +
                "• Справка — то, что ты читаешь.\n\n" +
                "Пэд — это одна «кнопка с звуком». Касаешься — звук играет. Выбранный пэд обведён бирюзовым."),
        Topic("Первый бит за 1 минуту",
            "1. Открой «Студия» и нажми ▶ — уже играет заготовка.\n" +
                "2. Коснись любого пэда — он выберется, а внизу покажутся его 16 шагов.\n" +
                "3. Тапай по шагам: оранжевый = в этот момент пэд сыграет.\n" +
                "4. Хочешь другой ритм — выбери паттерн B, C или D (их можно чередовать).\n" +
                "5. Не нравится звук — вкладка «Канал»: крути Pitch, Decay, Volume.\n" +
                "6. Готово? Вкладка FX → «Экспорт» → файл в папке Downloads."),
        Topic("Шаги, паттерны, такт",
            "Один такт — это 16 шагов (16 «шестнадцатых»). Считай их так:\n" +
                "1 e & a  2 e & a  3 e & a  4 e & a\n" +
                "Шаги 1, 5, 9, 13 — это четыре «доли» (раз, два, три, четыре). Они подсвечены светлее.\n\n" +
                "Паттерн — это целый записанный такт. A, B, C, D — четыре разных такта (например, куплет, припев, переход, сбивка).", true)
    )),
    Section("ЧТО ДЕЛАЕТ КАЖДАЯ КРУТИЛКА", listOf(
        Topic("BPM (темп)",
            "Beats Per Minute — сколько долей в минуту. Чем больше, тем быстрее бит.\n" +
                "• 70–95 BPM — расслабленный хип-хоп (boom bap)\n" +
                "• 120–128 — хаус\n" +
                "• 130–160 — трэп (часто играют в «половинном» ощущении)\n" +
                "Кнопка TAP: стучи пальцем в такт любой музыке — приложение само вычислит темп."),
        Topic("Swing (свинг)",
            "Сдвигает каждый второй шаг чуть позже. Ритм становится «пританцовывающим», живым, как будто играет человек, а не робот. " +
                "0% — ровно как метроном. 20–40% — классический хип-хоп/MPC-грув. Выше — почти шаффл. " +
                "Свинг заметнее всего на хэтах."),
        Topic("Volume и Pan",
            "Volume — громкость пэда. Двойной тап по фейдеру — вернуть на максимум.\n" +
                "Pan — где звук в стерео: L — слева, C — по центру, R — справа. Бочку и бас держат в центре, " +
                "хэты и шейкер чуть сдвигают в сторону, чтобы бит «дышал» шире.\n" +
                "Крутилки управляются движением пальца вверх/вниз; двойной тап — сброс на стандарт."),
        Topic("Pitch (высота тона)",
            "Делает звук выше или ниже, в полутонах (st): +12 — на октаву выше, −12 — на октаву ниже. " +
                "Заодно меняется скорость и длина звука, как у пластинки, которую крутят быстрее/медленнее. " +
                "Трюк: 808-бас на разных пэдах с разным Pitch — готовая басовая линия."),
        Topic("Attack (атака)",
            "Как быстро звук набирает громкость после касания. 0 мс — звук бьёт сразу (барабаны). " +
                "Больше — звук «вплывает» (подходит для пэдов, вокальных кусочков, шумов)."),
        Topic("Decay (затухание)",
            "Сколько звук живёт. «Полный» — играет весь сэмпл. Крути вниз — звук обрывается раньше и плавно гаснет. " +
                "Так из длинного открытого хэта получают короткий, а из 808 — короткий «щелчок»."),
        Topic("Filter: Cutoff и Resonance",
            "Одна крутилка «Filter» работает как у диджея.\n" +
                "• Центр — фильтр выключен.\n" +
                "• Влево — Low-pass: срезает высокие частоты, звук глохнет, как будто за стеной. Cutoff (частота среза) — граница, выше которой звука нет.\n" +
                "• Вправо — High-pass: срезает басы, звук становится тонким и звенящим.\n" +
                "Resonance — подчёркивает частоту среза и добавляет «свист»/«вау». Много резонанса = кислотный, звенящий звук."),
        Topic("Bitcrusher",
            "Уменьшает «разрешение» звука: меньше бит и пониженная частота дискретизации. " +
                "Получается зернистый, хрустящий, лоу-фай звук старых семплеров и игровых консолей. Чуть-чуть — тепло, много — «робот»."),
        Topic("Reverb",
            "Эффект пространства: как будто бит играет в комнате или зале. Mix — сколько эффекта слышно, Size — размер помещения. " +
                "Совет: не переборщи — на барабанах много реверба превращается в кашу. Лучше мало, на всём мастере."),
        Topic("Limiter и Master",
            "Master — общая громкость. Лимитер — «потолок»: он автоматически прижимает слишком громкие пики, " +
                "чтобы звук не хрипел и не искажался, сколько бы пэдов ни играло одновременно."),
        Topic("Режимы пэда: Gate, Trigger, Mute Group",
            "• Trigger — коснулся, звук доиграл до конца. Для ударных.\n" +
                "• Gate — звук играет, пока держишь палец; отпустил — затух. Для пэдов, баса, длинных нот.\n" +
                "• Mute Group — пэды одной группы (1–4) обрывают друг друга. Классика: закрытый и открытый хэт в одной группе — " +
                "когда бьёт закрытый, открытый замолкает, как на настоящей драм-машине. Ещё — чтобы мелодические кусочки не накладывались."),
        Topic("Vinyl Sim (SP-404)",
            "Звук «с пластинки»: лёгкое сжатие (компрессор), тёплый перегруз, чуть приглушённый верх и шум с потрескиванием. " +
                "Добавляет «грязный» андеграундный характер. 20–40% — приятный винил, 100% — старая заезженная запись."),
        Topic("Isolator (SP-404)",
            "Три полосы: Low (басы), Mid (середина), High (верха). Тащи фейдер вниз — полоса затихает, Kill — полностью вырезана. " +
                "Диджейский приём: на сбивке убери бас (Kill Low), а на «дропе» верни — выглядит очень мощно."),
        Topic("DJFX Looper (SP-404)",
            "Зажимаешь кнопку — приложение зацикливает последний микро-отрезок звука (1/2, 1/4, 1/8, 1/16 доли) и повторяет, пока держишь. " +
                "Получается «заикание» (stutter) для сбивок и переходов. Отпустил — бит продолжает как ни в чём не бывало."),
        Topic("Запись и Нарезка",
            "Канал → ● REC: пишет микрофон или звук, который играет на телефоне (системный звук; не все приложения разрешают его запись). " +
                "Нарезка: загрузи длинный файл, маркеры делят его на 16 частей. «По ударам» ставит маркеры автоматически " +
                "на резкие моменты, «Поровну» — равными кусками. Применить — и каждый кусок попадёт на свой пэд.")
    )),
    Section("КАК СОБРАТЬ БИТ", listOf(
        Topic("Главный принцип",
            "Бит стоит на трёх китах:\n" +
                "• Kick (бочка) — «бум», низ и сила. Задаёт, где «тяжело».\n" +
                "• Snare/Clap (малый барабан) — «тэк», акцент. Обычно на 2 и 4 долях.\n" +
                "• Hi-Hat (хэт) — «тс-тс», мелкая пульсация, которая держит время.\n\n" +
                "Правило: бочка и малый не должны играть одновременно, а хэт заполняет промежутки. " +
                "Сначала расставь малый (опора), затем бочку (грув), в конце хэты (оживление)."),
        Topic("Хип-хоп (Boom Bap, ~90 BPM)",
            "Спокойный, качающий, с небольшим свингом (25–35%).\n\n" +
                "Шаг:    1 . . . 5 . . . 9 . . . 13 . . .\n" +
                "Kick :  X . . . . . . X . . X . . . . .\n" +
                "Snare:  . . . . X . . . . . . . X . . .\n" +
                "HiHat:  X . X . X . X . X . X . X . . .\n\n" +
                "Как строить: 1) Малый на шаги 5 и 13 (доли 2 и 4). 2) Бочка на 1, а затем «подпрыгивает» на 8 и 11 — вот что качает. " +
                "3) Хэт каждый второй шаг. 4) Включи Swing ≈ 30% и сделай хэты чуть тише (Volume 60%). " +
                "5) Для пыли добавь Vinyl Sim 30%.", true),
        Topic("Трэп (~140 BPM, «половинное» ощущение)",
            "Малый/клэп всего один раз за такт — на 9-м шаге — оттого ритм кажется медленным. Зато хэты частые.\n\n" +
                "Шаг:    1 . . . 5 . . . 9 . . . 13 . . .\n" +
                "Kick :  X . . X . . . . . . X . . . . .\n" +
                "808  :  X . . . . . . . . . X . . . . .\n" +
                "Clap :  . . . . . . . . X . . . . . . .\n" +
                "HiHat:  X . X . X . X . X . X . X X X X\n\n" +
                "Как строить: 1) Клэп + малый на шаг 9. 2) Бочка и 808 на шаг 1 и 11. 3) Хэты каждые два шага, а в конце такта (13–16) — " +
                "ролл шестнадцатыми. 4) Pitch 808-баса подбери «на слух» под мелодию; режим Mute Group, чтобы басы не накладывались. " +
                "5) Открытый хэт на 8-й шаг добавляет качели.", true),
        Topic("Хаус (~124 BPM)",
            "Бочка на каждую долю («четыре в пол»), клэп на 2 и 4, открытый хэт между бочками.\n\n" +
                "Шаг:    1 . . . 5 . . . 9 . . . 13 . . .\n" +
                "Kick :  X . . . X . . . X . . . X . . .\n" +
                "Clap :  . . . . X . . . . . . . X . . .\n" +
                "Open :  . . X . . . X . . . X . . . X .\n\n" +
                "Совет: хэты в одной Mute Group — тогда открытый обрывается закрытым.", true),
        Topic("Что делать дальше (чтобы бит «ожил»)",
            "• Меняй громкость пэдов: хэты тише бочки, бас громче.\n" +
                "• Не забивай все шаги — паузы тоже создают грув.\n" +
                "• Сделай паттерн B вариацией A (добавь пару ударов) — чередуй, чтобы не наскучило.\n" +
                "• На сбивках используй Isolator и DJFX Looper.\n" +
                "• Загрузи свои сэмплы на пэды или нарежь любимую пластинку — и поверх положи барабаны.")
    ))
)

@Composable
fun HelpScreen(vm: BeatViewModel, goStudio: () -> Unit) {
    LazyColumn(
        Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        item {
            Card("СПРАВОЧНИК БИТМЕЙКЕРА") {
                Text("Нажми на тему, чтобы раскрыть. Ниже — готовые примеры: загрузи и послушай, как это звучит.",
                    color = BF.dim, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Hip-Hop → A", false, accent = BF.teal) { vm.loadPreset(0, 0); vm.setPattern(0); goStudio() }
                    Chip("Trap → B", false, accent = BF.teal) { vm.loadPreset(1, 1); vm.setPattern(1); goStudio() }
                    Chip("House → C", false, accent = BF.teal) { vm.loadPreset(2, 2); vm.setPattern(2); goStudio() }
                }
                Text("Пример заменит выбранный паттерн и поставит подходящий BPM. Потом нажми ▶ в Студии.",
                    color = BF.dim, fontSize = 12.sp)
            }
        }
        for (s in GUIDE) {
            item { Text(s.title, color = BF.orange, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
            items(s.topics) { t -> TopicCard(t) }
        }
    }
}

@Composable
private fun TopicCard(t: Topic) {
    var open by remember(t) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BF.panel)
            .clickable { open = !open }.padding(12.dp)
    ) {
        Row {
            Text(t.title, color = BF.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (open) "▲" else "▼", color = BF.dim)
        }
        if (open) Text(
            t.body, color = BF.text.copy(alpha = 0.9f), fontSize = if (t.mono) 11.sp else 13.sp,
            lineHeight = if (t.mono) 15.sp else 19.sp,
            fontFamily = if (t.mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
