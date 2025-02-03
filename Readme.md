# FLEXOR: File LEXical Organizer

## 概要
FLEXORは、CSVファイルや固定長SAMファイルを取り込み、帳票生成やデータ連携用のCSVファイルを出力するためのツールです。このツールは、顧客の運用担当者や自社のシステム運用担当が利用することを想定しています。

## 機能
- **CSVファイルのマージ**: 複数のCSVファイルを統合し、指定された形式で出力します。
- **SAMファイルの処理**: 固定長のSAMファイルを読み込み、必要なデータを抽出してCSV形式で出力します。
- **設定ファイルによる柔軟なデータ処理**: JSON形式の設定ファイルを使用して、データの書式や処理方法を柔軟に指定できます。
- **GUIサポート**: GUIを使用して、ユーザーが簡単にファイルを指定し、処理を開始できます。

## 使い方

### 1. プロジェクトのクローン
リポジトリをクローンします。

```bash
git clone <repository_url>
cd <repository_directory>
```

### 2.Jarファイルの取得

Tools\jar_download.batを実行して、関連ライブラリを取得してください。

### 3. 設定ファイルの作成
`sample.json`を参考にして、必要な設定を行ったJSONファイルを作成します。以下は設定ファイルの例です。

```json
{
    "title": "後期広域用サンプルツール",
    "description": "このツールは後期広域用CSVファイルをマージするためのものです。",
    "gui": true,
    "input": {
    	/* 通常のCSV */
        "tcat_atena": {
            "path": "data/tcat_atena.csv",
            "type": "csv",
            "option": ""
        },
    	/* ヘッダー無しCSVの場合、columnsy要素を付けてヘッダを指定 */
        "tcat_koza": {
            "path": "data/tcat_koza.csv",
            "type": "csv",
            "option": "",
        "columns":[
				"JICHTICD",
				"KOJINNO",
				"ZIMKCD",
				"KUZTURKKBN"
			]

        },
	/* フォルダを指定して、その中のファイルを処理する */
	"tcat_denwano":{ "folder":"data/", "filter":"tcat_denwano_.*csv" ,"type":"csv" },
	/* 固定長SAMファイルの場合、レコード長、列定義を記述する */
        "D99": {
            "path": "data/D99",
            "type": "sam",
            "encode": "utf-8",
            "record_length": 100,
            "option": "",
            "filter": {
                "key": "レコード識別子",
                "value": "2"
            },
            "columns": [
                {"name": "レコード識別子", "length": 1, "encode": "utf-8"},
                {"name": "処理連番", "length": 8, "encode": "utf-8"},
                {"name": "氏名", "length": 40, "encode": "utf-16"},
                {"name": "処理枝番", "length": 8, "encode": "utf-8"}
            ]
        },
	/* Excelデータの場合、シートと読込範囲を指定する */
	"excel_data": {
		"path": "data/sample.xlsx",
		"type": "excel",
		"sheet": "Sheet1",
		"startRow": 1,
		"startCol": 0,
		"rowCount": 100,
		"colCount": 5
	}

    },
    /* 読み込み後に任意のSQLを発行して索引やワークテーブルを作る */
    	"customQuery":[
		"create table tcat_atena2 as select kojinno,shmiknj from tcat_atena;",
		"create table tcat_atena3 as select kojinno,shmiknj from tcat_atena;"
	],
	/* 出力部分 */
	"output": {
		/* 単純なCSVの場合、出力パス、形式と抽出SQLを書く */
		"out1":{
			"query":[
				"SELECT * ",
				"FROM D61"
			],
			"path": "out/sample-out1-$DATE$-$TIME$.csv",
			"type":"csv",
			"encoding": "UTF-8"
		},
		/* 固定長の場合、列定義と改行区切り有無を記述する */
		"out2":{
			"query":[
				"SELECT KOJINNO,SHMIKNJ ",
				"FROM tcat_atena2"
			],
			"path": "out/sample-out2-$DATE$-$TIME$.csv",
			"type":"sam",
			"encoding": "UTF-8",
			"lineSepareted":false,
			"columns": [
				{ "name": "KOJINNO", "length": 30,"padding":"0","encode":"UTF-8" },
				{ "name": "SHMIKNJ", "length": 100,"padding":" ","encode":"UTF-16" }
			]
		}
	}

}
```

### 3. 実行
以下のような `sample-exec.bat`を作成してツールを実行します。

```sample.bat
<Toolsフォルダ配置パス>\exec.bat sample.json
```

### 4. 出力ファイル
処理が完了すると、指定した出力パスにCSVファイルが生成されます。

## 注意事項
- 入力ファイルのパスや形式が正しいことを確認してください。
- 設定ファイルの内容を適切に設定することで、ツールの動作をカスタマイズできます。

## ライセンス
このプロジェクトはMITライセンスの下で提供されています。

# 更新履歴

## 2025/1/12 

- 初版

## 2025/1/31

- 複数ファイルの出力に対応しました。

- 固定長ファイルの出力に対応しました。

- Excelファイルの入力に対応しました。

- ヘッダ無しCSVファイルの入力に対応しました。

- フォルダを指定して、その中のファイルを処理する機能に対応しました

## 2025/2/3

- CSV出力時のパフォーマンス改善

- ヘッダ／トレイラー／フッターレコード出力対応およびサンプル追加

- DuckDB/H2Databaseの選択利用
