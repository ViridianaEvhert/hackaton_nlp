import traceback
from time import sleep
from pathlib import Path
from random import random

import requests
from bs4 import BeautifulSoup


def extract_content(url: str, outdir: Path):
    page = requests.get(url)
    soup = BeautifulSoup(page.content, "html.parser")

    outname = url.rsplit("/", maxsplit=1)[1].split("?idiom")[0].replace("-", "_")
    outfile = outdir / f"{outname}.txt"

    print(f"processing {outfile}")
    try:
        with open(outfile, "w") as dst:
            h1 = soup.find('h1').get_text().strip()
            h2 = soup.find('h2').get_text().strip()
            dst.write(f"<url>{url}</url>\n")
            dst.write(f"<h1>{h1}</h1>\n")
            dst.write(f"<h2>{h2}</h2>\n")
            for idx, x in enumerate(soup.find("div", {"class": "article-body"}).find_all("p")):
                dst.write(f"<id>{idx}</id>{x}\n")
    except:
        traceback.print_exc()


def pagenator(parent_url: str, outdir: Path) -> str:
    page = requests.get(parent_url)
    soup = BeautifulSoup(page.content, "html.parser")

    next_url = None
    for link in soup.find_all("a"):
        sleep(random())
        href = "https://www.gob.mx" + link.get("href").replace('\\\"', "")
        if "estenogra" in href:
            extract_content(href, outdir)
        elif "order=DESC&page=" in href:
            next_url = href
    return next_url


def main():
    parent_url = "https://www.gob.mx/presidencia/es/archivo/articulos?filter_id=&filter_origin=archive&idiom=es&order=DESC&page=1&style=list&tags=&utf8=%E2%9C%93"
    outdir = Path("Data_v3")
    outdir.mkdir(parents=True, exist_ok=True)

    url = parent_url
    # while url is not None:
    for page in range(1, 212):
        url = parent_url.replace("page=1&", f"page={page}&")
        print(f"at: {url}")
        pagenator(url, outdir)


if __name__ == "__main__":
    main()