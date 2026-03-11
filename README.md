<p align="center">
  <h1 align="center">TimeRamp</h1>
  <p align="center">
    <b>"Time waits for no one. Neither does the difficulty."</b><br>
    Dynamic Risk of Rain 2-style difficulty for Minecraft 1.21 – 1.21.10
  </p>

  <p align="center">
    <a href="https://github.com/Keyser2356/ROR2Difficulty"><img src="https://img.shields.io/github/stars/Keyser2356/ROR2Difficulty?style=for-the-badge&color=FF4444" alt="Stars"></a>
    <a href="https://github.com/Keyser2356/ROR2Difficulty/releases"><img src="https://img.shields.io/github/downloads/Keyser2356/ROR2Difficulty/total?style=for-the-badge&color=FFD700" alt="Downloads"></a>
    <a href="https://papermc.io"><img src="https://img.shields.io/badge/Paper-1.21--1.21.10-FF9900?style=for-the-badge&logo=java&logoColor=white" alt="Paper 1.21"></a>
    <a href="https://purpurmc.org"><img src="https://img.shields.io/badge/Purpur-Supported-9B59B6?style=for-the-badge" alt="Purpur"></a>
  </p>
</p>

---

### 🔥 What is this?

A plugin that transforms vanilla Minecraft into a **true roguelike with scaling difficulty**:

- The difficulty coefficient **grows every minute**.
- Slaying bosses and elite mobs **significantly accelerates** the scaling.
- Player death **resets a portion** of the progress.
- Everything scales: mob health, damage, speed, elite spawn rates, and frequency.

The HUD displays the current coefficient and "Insanity Level":

Drizzle → Rain → Monsoon → HAHAHAHA → **I'M ALREADY DEAD**

Perfect for hardcore playthroughs, speedruns, and servers looking for **constant tension**.

---

### ⚙️ How it works (In short)

```text
coeff = (base + minutes × timeRate + eventBonus)
      × stageFactor
      × playerCountFactor
```

* **eventBonus** → +0.5…2.0 for a boss, +0.2…0.8 for an elite mob.
* **player death** → −0.8…−1.5 (configurable).
* **player count** → non-linear scaling for higher player counts.

Everything is fully customizable in `config.yml`.

---

### ✨ Key Features

* Time-based difficulty scaling (inspired by RoR2).
* Event-driven reactions: Bosses / Elites / Deaths.
* Dynamic scaling based on player count.
* Sleek HUD with difficulty tier names.
* Attribute-only modifications (no new entities or items required).
* Fully standalone plugin (no dependencies).
* Supports Paper / Purpur / Folia 1.21–1.21.10.

---

### 📸 Screenshots

<img width="1920" height="1017" alt="Difficulty Gameplay 1" src="https://github.com/user-attachments/assets/9b586a40-d740-4916-b653-91fda1197dc1" />
<img width="3840" height="2160" alt="Difficulty Gameplay 2" src="https://github.com/user-attachments/assets/5116d85f-0c34-4c5c-ba12-1b816ed0fb65" />

---

### 🚀 Setup (30 seconds)

1. Download the [latest version → TimeRamp.jar](https://github.com/Keyser2356/ROR2Difficulty/releases).
2. Drop it into your `plugins` folder.
3. Restart your server.
4. Open and configure `plugins/TimeRamp/config.yml`.
5. Run the command:
```
/difadmin reload
```


6. Done. The clock is already ticking.

---

<p align="center">
<b>Good luck. The timer has started.</b>
