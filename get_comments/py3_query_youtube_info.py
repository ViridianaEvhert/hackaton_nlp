from time import sleep
from shutil import rmtree
from warnings import warn
from typing import Dict
from pathlib import Path
from dataclasses import dataclass

from unidecode import unidecode
from googleapiclient import discovery
import json


@dataclass
class ParseChannelVideos:
    channel_id: str = r"UCxEgOKuI-n-WOJaNcisHvSg"
    key: str = "AIzaSyBGWg9cdTpwwIzs7YiUcDit10lAk9LzW3g"

    def __post_init__(self):
        self.youtube = discovery.build(
            serviceName="youtube",
            version="v3",
            developerKey=self.key,
        )

    def _get_channel_info(self):
        """Fetch high level info from the target channel. This includes the
        total number of views, subscribers and videos.
        """
        channel_info = self.youtube.channels().list(
            part="snippet, contentDetails, statistics",
            id=self.channel_id,
            fields="items(statistics(viewCount,subscriberCount,videoCount))",
        ).execute()
        self.channel_info = channel_info["items"][0]["statistics"]

    @staticmethod
    def _flatten_dict_level(input_dict: Dict, level: str) -> Dict:
        """Flat a top level entry from a nested dictionary."""
        output_dict = input_dict.copy()
        level_to_flatten = output_dict.pop(level, None)

        if level_to_flatten is None:
            return output_dict

        output_dict.update(**level_to_flatten)
        return output_dict

    def _get_playlists_ids(self):
        """Fetch high level info of the playlists to be retrived. This includes
        playlist's id, title and description.
        """
        playlists_info = self.youtube.playlists().list(
            part="snippet",
            channelId=self.channel_id,
            maxResults=50,
            fields="items(id,snippet(title,description))",
        ).execute()

        buffer = [
            self._flatten_dict_level(x, "snippet") for x in playlists_info["items"]
        ]

        self.playlists_info = buffer

    def _get_playlist_items(
        self, playlist_id: str, playlist_name: str, items_per_token: int = 50
    ):
        """Retrieve videos' info from the requested playlist.
        """
        # We don't need to retrive all metadata info, only a subset of it is
        # useful to our purposes. These are the relevant fields.
        fields = (
            f"nextPageToken,"
            f"pageInfo(totalResults),"
            f"items("
                f"snippet("
                    f"publishedAt,"
                    f"title,"
                    f"description,"
                    f"position,"
                f"),"
                f"contentDetails(videoId)"
            f")"
        )
        total_playlist_videos = None
        retrived_playlist_videos = 0
        next_token = None
        buffer = []

        # To avoid looping forever, we set a hard threshold on the number of
        # tokens to be retrieved.
        max_tokens = int(self.channel_info["videoCount"]) // 50
        for _ in range(max_tokens):
            playlist_items = self.youtube.playlistItems().list(
                part="snippet,contentDetails,id,status",
                playlistId=playlist_id,
                maxResults=items_per_token,
                pageToken=next_token,
                fields=fields,
            ).execute()

            # Update buffer storing retrived videos.
            buffer.extend(playlist_items["items"])

            # Keep track of how many playlist items (videos) have been retrieved.
            total_playlist_videos = playlist_items["pageInfo"]["totalResults"]
            retrived_playlist_videos += len(playlist_items["items"])

            # Did we finish fetching all videos in the playlist?
            next_token = playlist_items.get("nextPageToken", False)
            if not next_token:
                break

        # Ensure we retrived all videos.
        if retrived_playlist_videos < total_playlist_videos:
            warn(
                f"Not all tokens from playlist '{playlist_name}' were retrived. "
                f"Retrived videos: {retrived_playlist_videos} / {total_playlist_videos}"
            )

        return [
            self._flatten_dict_level(
                self._flatten_dict_level(x, "contentDetails"), "snippet",
            )
            for x in buffer
        ]

    def get_playlists_videos(self):
        """Retrive videos' info from all playlists in channel.
        """
        self._get_channel_info()
        self._get_playlists_ids()

        playlists_videos = {}
        for playlist in self.playlists_info:
            # Normalize playlist name.
            name = unidecode(playlist["title"]).strip(" ").replace(" ", "_").lower()

            playlists_videos[name] = self._get_playlist_items(
                playlist["id"], playlist["title"], 50
            )
            # break

        return playlists_videos

    def get_playlist_comments(self):
        data=self.get_playlists_videos()
        comentarios = {}
        for key in data.keys():
            # only 1 video for playlist becuase exceds query limits 
            for i in range(0,1):  #cambiar por for i in range(0,len(data[key])):  pero puede exceder el limite de peticiones a youtube
                videoid = str(data[key][i]['videoId'])
                title = str(data[key][i]['title'])
                playlist = str(key)
                print(title)
                print(videoid)
                comentarios[playlist,title]=self.youtube.commentThreads().list(
                                                    part='snippet,replies',
                                                    videoId=videoid
                           ).execute()
        return comentarios        
        


def write_video_descriptions(outdir: Path, data: Dict):
    if outdir.is_dir():
        rmtree(outdir, ignore_errors=True)
        sleep(2.0)
    cnt = 0
    for playlist, videos in data.items():
        print(f"processing: {playlist}")
        subdir = outdir / f"{playlist}"
        subdir.mkdir(parents=True, exist_ok=True)

        for vid in videos:
            cnt += 1
            fecha = vid["publishedAt"].split("T")[0].replace("-", "_")
            title = (
                unidecode(vid["title"])
                .strip(" ")
                .replace(",", "")
                .replace(".", "")
                .replace("\"", "")
                .replace("\'", "")
                .replace("-", "")
                .replace(" ", "_")
                .lower()
            )
            outfile = subdir / f"{fecha}_title_{title}.txt"
            texto = [x for x in vid["description"].split("\n\n") if not "http" in x]

            with open(outfile, "w") as dst:
                dst.write(f"<h1>{vid['publishedAt']}</h1>\n")
                dst.write(f"<h2>{vid['title']}</h2>\n")
                for sentence in texto:
                    dst.write(f"<p>{sentence}</p>\n")
    print(f"{cnt} files were processed")

def write_video_comments(outdir: Path, data: Dict):
   for videos in data.items():
       print(videos[0][0])
       print("=============Comentarios=====================")
       print(videos[0][1])
       print(f"processing: {videos[0][0]}")
       subdir = outdir / f"{videos[0][0]}"
       subdir.mkdir(parents=True, exist_ok=True)
       title = (unidecode(videos[0][1])
                .strip(" ")
                .replace(",", "")
                .replace(".", "")
                .replace("\"", "")
                .replace("\'", "")
                .replace("-", "")
                .replace(" ", "_")
                .lower()
        )
       outfile = f'{subdir}/{title}_comments.txt'
       print(outfile)
       with open(outfile, "w") as dst:
           dst.write(json.dumps(videos[1]))

def main():
    outdir = Path(r"/home/fernando/nlp_hackaton/hackaton_nlp/Data_test")

    parser = ParseChannelVideos()
    data = parser.get_playlists_videos()
    comentarios = parser.get_playlist_comments()
    #write_video_descriptions(outdir, data)
    write_video_comments(outdir,comentarios)
    

    
if __name__ == "__main__":
    main()
