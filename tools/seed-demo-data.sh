#!/usr/bin/env bash
# Fills an Android *emulator* with made-up contacts and conversations, for store screenshots.
# Everything here is fictional. Refuses to run on a real phone, since it writes to the SMS store.
#
#   tools/seed-demo-data.sh [emulator-serial]
set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
SERIAL="${1:-emulator-5554}"
adb() { "$ADB" -s "$SERIAL" "$@"; }

if [ "$(adb shell getprop ro.boot.qemu | tr -d '\r')" != "1" ]; then
    echo "$SERIAL is not an emulator; not touching it." >&2
    exit 1
fi

# The shell may write to the SMS store only with this app-op.
adb shell appops set com.android.shell WRITE_SMS allow

now_ms=$(( $(date +%s) * 1000 ))
min=60000

contact() { # name number
    adb shell content insert --uri content://com.android.contacts/raw_contacts \
        --bind account_type:n: --bind account_name:n:
    local id
    id=$(adb shell content query --uri content://com.android.contacts/raw_contacts --projection _id \
        | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n | tail -1)
    adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id \
        --bind mimetype:s:vnd.android.cursor.item/name --bind \"data1:s:$1\""
    adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id \
        --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$2 --bind data2:i:2"
}

sms() { # number minutes-ago type(1 in, 2 out) read body (no double quotes in the body)
    adb shell "content insert --uri content://sms --bind address:s:$1 \
        --bind date:l:$(( now_ms - $2 * min )) --bind type:i:$3 --bind read:i:$4 \
        --bind seen:i:1 --bind \"body:s:$5\""
}

contact "Maya Fernandes" "+15550101"
contact "Arjun Rao" "+15550102"
contact "Lena Park" "+15550103"
contact "Sam Okafor" "+15550104"
contact "Dad" "+15550105"
contact "Nora Lindqvist" "+15550106"

sms +15550102 400 2 1 "Are we still on for climbing tomorrow?"
sms +15550102 395 1 1 "Yes! 7am at the wall. I'll bring the spare harness"
sms +15550102 390 2 1 "Perfect. Coffee after?"
sms +15550102 30 1 0 "Running 10 min late, save me a spot 🧗"

sms +15550101 1700 1 1 "Back home! That drive was long but so worth it"
sms +15550101 1695 2 1 "Same. Still can't believe we saw the northern lights"
sms +15550101 1690 1 1 "Best night of the whole trip 🌌"
sms +15550101 1600 2 1 "We have to go back next winter"
sms +15550101 1590 1 1 "Already looking at cabins"
sms +15550101 200 1 1 "Did you see the photos from the trip?"
sms +15550101 198 2 1 "Just now. That sunset over the lake is unreal"
sms +15550101 190 1 1 "Printing that one for the hallway"
sms +15550101 15 2 1 "Can you send me the full-size one?"
sms +15550101 12 1 0 "Dinner at mine on Friday? I'm making the lasagna"

sms VM-NOVABK 5 1 0 "482913 is your Nova Bank verification code. It expires in 10 minutes. Never share it with anyone."

sms +15550103 1500 2 1 "Thanks for the book recommendation"
sms +15550103 1440 1 1 "Halfway through it already, can't put it down"
sms +15550103 60 2 1 "No spoilers, I'm only on chapter 4 📖"

sms +15550105 3000 1 1 "Landed safely. Call you tonight"
sms +15550105 2990 2 1 "Great, have a good rest"
sms +15550105 180 1 1 "Don't forget Grandma's birthday on Sunday"

sms +15550104 4400 1 1 "The new album is out!"
sms +15550104 4390 2 1 "On repeat already"

sms +15550106 6000 2 1 "Sent you the slides for Monday"
sms +15550106 5990 1 1 "Got them, looks great. See you at 10"

sms AD-PARCEL 900 1 1 "Your parcel is out for delivery today between 2 and 5 pm."

echo "Seeded $SERIAL."
