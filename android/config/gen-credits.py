#!/usr/bin/env python3
"""gen-credits.py: the Licenses rows for what Gradle cannot see, from upstream's own notice page.

data/copyright.html is the list CoMaps keeps of every project, data set, font and icon set built
into the app, each with its copyright line and the full text of its license. This script turns
each entry into a row (config/libraries/up-*.json) and each license text into
config/licenses/up-*.json, which the AboutLibraries plugin merges into the list the build makes.

Run it after every move to a newer CoMaps release:   python3 android/config/gen-credits.py
It rewrites only the up-* files; the hand-written ones beside them stay as they are.
"""
import glob, html, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PAGE = os.path.join(HERE, "..", "..", "data", "copyright.html")

# The page's own names for its license sections, in plain words for the row.
TITLES = {
    "agg-license": "Anti-Grain Geometry license (BSD-3-Clause)",
    "mit-license": "MIT License",
    "bsd2-license": "BSD 2-Clause License",
    "bsd3-license": "BSD 3-Clause License",
    "boost-license": "Boost Software License 1.0",
    "icu-license": "ICU License",
    "lgpl21-license": "GNU Lesser General Public License 2.1",
    "apache2-license": "Apache License 2.0",
    "artistic-license": "Artistic License 2.0",
    "cmake-support-license": "CMake Metal support files license (MIT)",
    "freetype-license": "FreeType Project License",
    "dejavufonts-license": "DejaVu Fonts License",
    "sil-license": "SIL Open Font License 1.1",
    "ogl3": "Open Government Licence v3",
    "zlib-license": "zlib License",
    "cc0-1": "CC0 1.0 Universal",
    "cc-by-sa-3": "CC BY-SA 3.0",
    "cc-by-4": "CC BY 4.0",
    "cc-by-sa-4": "CC BY-SA 4.0",
    "gpl2-license": "GNU General Public License 2.0",
}


def text_of(fragment):
    """HTML to plain text: one blank line between blocks, list items on their own lines."""
    t = re.sub(r"<(script|style)\b.*?</\1>", "", fragment, flags=re.S)
    t = re.sub(r"\s+", " ", t)
    t = re.sub(r"<br\s*/?>", "\n", t)
    t = re.sub(r"<li\b[^>]*>", "\n- ", t)
    t = re.sub(r"</(p|h\d|div|ul|ol|pre|blockquote|dl|dd|dt|tr|table)>", "\n\n", t)
    t = re.sub(r"<(p|h\d|div|ul|ol|pre|blockquote|dl|dd|dt|tr|table)\b[^>]*>", "\n\n", t)
    t = html.unescape(re.sub(r"<[^>]+>", "", t))
    lines = [re.sub(r"[ \t ]+", " ", l).strip() for l in t.split("\n")]
    out = []
    for l in lines:
        if l or (out and out[-1]):
            out.append(l)
    return "\n".join(out).strip()


def slug(name):
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def main():
    page = open(PAGE, encoding="utf-8").read()

    # license sections: from each <h2 id=...> to the next <h2
    sections = {}
    heads = list(re.finditer(r"<h2\b[^>]*\bid=\"([^\"]+)\"[^>]*>", page))
    for i, m in enumerate(heads):
        end = heads[i + 1].start() if i + 1 < len(heads) else page.find("</body>")
        sections[m.group(1)] = text_of(page[m.start():end])

    libs, used, problems = [], set(), []
    credits = page[:heads[0].start()]  # every list above the license texts
    for ul in re.finditer(r"<ul\b[^>]*>(.*?)</ul>", credits, flags=re.S):
        for li in re.finditer(r"<li\b[^>]*>(.*?)</li>", ul.group(1), flags=re.S):
            item = li.group(1)
            anchors = re.findall(r"href=\"#([^\"]+)\"", item)
            site = re.search(r"href=\"(https?://[^\"]+)\"", item)
            plain = text_of(re.sub(r"<br\s*/?>", " ; ", item)).replace("\n", " ")
            parts = [p.strip() for p in re.split(r"\s*;\s*", plain) if p.strip()]
            name = parts[0]
            if name.startswith("©"):  # an entry that is only a link and a copyright line
                parts.insert(1, name)
                name = site.group(1).rstrip("/").rsplit("/", 1)[-1] if site else name
            holder = next((p for p in parts[1:] if "©" in p or "Copyright" in p), "")
            holder = re.sub(r"\s+(Apache|MIT|BSD2?|Boost|Zlib|ICU|Artistic|CC BY(-SA)? [\d.]+|CC0 1\.0|Freepik Free|GNU General Public|Open Government)\b.*$", "", holder)
            holder = re.sub(r"\s+(\S+\s)?(License|Licence|OFL|FTL|LGPL|Universal \.?|Shareware|Public Domain)\.?$", "", holder).strip(" ;.")
            holder = re.sub(r"^(©|Copyright( \(c\))?)\s*", "", holder)
            uid = "up-" + slug(name)
            entry = {"uniqueId": uid, "name": name}
            if holder:
                entry["developers"] = [{"name": holder}]
            if site:
                entry["website"] = site.group(1).replace("http://", "https://")
            if anchors:
                ids = []
                for a in anchors:
                    if a not in sections:
                        problems.append(f"{name}: no section #{a} on the page")
                        continue
                    ids.append("up-" + a)
                    used.add(a)
                entry["licenses"] = ids
            else:
                # no text on the page (shareware, public domain, a vendor's own terms): the row
                # shows upstream's own line about it
                entry["licenses"] = [uid + "-terms"]
                used.add((uid + "-terms", plain))
            libs.append(entry)

    for d in ("libraries", "licenses"):
        os.makedirs(os.path.join(HERE, d), exist_ok=True)
        for old in glob.glob(os.path.join(HERE, d, "up-*.json")):
            os.remove(old)

    def put(d, name, obj):
        with open(os.path.join(HERE, d, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
            f.write("\n")

    seen = set()
    for e in libs:
        if e["uniqueId"] in seen:
            problems.append(f"{e['name']}: listed twice")
            continue
        seen.add(e["uniqueId"])
        put("libraries", e["uniqueId"], e)
    for u in used:
        if isinstance(u, tuple):
            put("licenses", u[0], {"hash": u[0], "name": "Terms as upstream states them", "content": u[1]})
        else:
            if u not in TITLES:
                problems.append(f"license section #{u} has no title in TITLES")
            if len(sections[u]) < 200:
                problems.append(f"license section #{u} is suspiciously short")
            put("licenses", "up-" + u, {"hash": "up-" + u, "name": TITLES.get(u, u), "content": sections[u]})

    print(f"{len(seen)} rows, {len(used)} license texts")
    for p in problems:
        print("PROBLEM:", p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
