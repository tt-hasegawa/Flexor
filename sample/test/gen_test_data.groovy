import groovy.sql.Sql
import java.io.File

new File('../data/D99').withOutputStream { out ->
    int FIXED_SZ = 100
    int rec_sz = 0
    // header
    rec_sz = FIXED_SZ
    rec_sz -= write(out, '1')
    rec_sz -= write(out, '123456')
    write(out, ''.padLeft(rec_sz, ' '))

    // data
    for (int i = 1; i < 1000; i++) {
        rec_sz = FIXED_SZ
        rec_sz -= write(out, '2')
        rec_sz -= write(out, "${i}".padLeft(8, '0'))
        rec_sz -= write(out,'あいうえおかきくけこ'.padRight(20,'　'),'utf-16')
        rec_sz -= write(out, "${i}".padLeft(8, '0'))
        write(out, ''.padLeft(rec_sz, ' '))
    }
    rec_sz = FIXED_SZ
    rec_sz -= write(out, '3')
    write(out, ''.padLeft(rec_sz, ' '))
}


def dbUrl = 'jdbc:oracle:thin:@m-31058-pgdb2:1521:nad' // サーバのURL
def user = 'nadora' // ユーザー名
def password = 'arodan' // パスワード
def sql = Sql.newInstance(dbUrl, user, password, 'oracle.jdbc.OracleDriver')

new File('../data/tcat_atena.csv').withWriter("UTF-8") { writer ->
    // メタデータから列名を取得
    def resultSet = sql.rows('SELECT * FROM tcat_atena WHERE ROWNUM = 1')
    def columnNames = resultSet[0].keySet()

    // ヘッダを書き込む
    writer.writeLine(columnNames.join(','))

    // データを取得してCSVに書き込む
    sql.eachRow('SELECT * FROM tcat_atena') { row ->
        writer.writeLine(columnNames.collect { row[it] }.join(','))
    }
}
new File('../data/tcat_koza.csv').withWriter("UTF-8") { writer ->
    // メタデータから列名を取得
    def resultSet = sql.rows('SELECT * FROM tcat_koza WHERE ROWNUM = 1')
    def columnNames = resultSet[0].keySet()
    // データを取得してCSVに書き込む
    sql.eachRow('SELECT * FROM tcat_koza') { row ->
        writer.writeLine(columnNames.collect { row[it] }.join(','))
    }
}

sql.close()

def write(def outputStream, def string, def encoding = 'utf-8') {
    byte[] bytes = string.getBytes(encoding)
    outputStream.write(bytes)
    return bytes.length
}


