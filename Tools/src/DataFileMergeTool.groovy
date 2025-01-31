import java.sql.DriverManager
import groovy.sql.*
import groovy.json.*
import java.nio.file.*
import groovy.swing.SwingBuilder
import javax.swing.*
import java.awt.BorderLayout
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook

int WIDTH=800;

// 設定ファイルを読み込み
if (args.size() == 0) {
    println 'Config file does not exists.'
    log 'Config file does not exists.'
    System.exit(9)
}
if (!Files.exists(Paths.get(args[0]))) {
    println "Config file:${args[0]} does not exist."
    log "Config file:${args[0]} does not exist."
    System.exit(9)
}

def config = args[0]
def json = new JsonSlurper().parse(new File(config))

// JSONから必要なデータを抽出
def title = json.title ?: 'DataFile Merge Tool'
def description = json.description ?: 'This tool is for merging Data files.'

Map tables = [:]
json.input.each { t ->
    log "Input:${t.key}"
    tables.put(t.key, t.value)
}
Map outputs=[:]
json.output.each{ t ->
    log "Output:${t.key}"
    outputs.put(t.key, t.value)
}

String customQuery = "";
json.customQuery.each{line ->
    log "CustomQuery:${line}"
    customQuery+=line;
    customQuery += ' '
}

new File('temp/db').delete()
new File('temp').mkdir()
Class.forName('org.duckdb.DuckDBDriver')

// GUIフラグのチェック
if (json.gui == true) {
    def swingBuilder = new SwingBuilder()
    swingBuilder.edt {
        def frame = frame(title: title, size: [WIDTH, 300 + tables.size() * 30], show: true, defaultCloseOperation: JFrame.EXIT_ON_CLOSE) {
            borderLayout()
            panel(constraints: BorderLayout.NORTH) {
                label(text: description)
            }
            panel(constraints: BorderLayout.CENTER) {
                gridLayout(columns: 1, rows: 2)
                // ファイルパス入力用のグループペイン
                panel(constraints: BorderLayout.NORTH) {
                    gridLayout(columns: 2, rows: tables.size() + 1)
                    tables.each { key, value ->
                        label("Input File : ${key} ")
                        textField(id: "${key}File", text: value.path, columns: 400)
                    }
                }
                panel(){
                    gridLayout(columns: 1, rows: 1)
                    textArea(id: 'sqlTextField', rows: 5, columns: 40)
                }
            }
            panel(preferredSize:[WIDTH,60],constraints: BorderLayout.SOUTH ) {
                gridLayout(columns: 1, rows: 2)
                panel(preferredSize:[WIDTH,30]){
                    gridLayout(columns: 3, rows: 1)
                    label('Output File/Encoding: ')
                    textField(id: 'outputFile', text: "sample-out.txt", columns: 400)
                    textField(id: 'outputEncoding', text: "UTF-8", columns: 400)
                }
                panel(preferredSize:[WIDTH,30]){
                    gridLayout(columns: 4, rows: 1)
                    // ファイル取込ボタン
                    button(text: 'ファイル取込', actionPerformed: {
                        tables.each { key, value -> value.path = swingBuilder."${key}File".text }
                        importFileToDb(tables,customQuery)
                        // 処理完了メッセージを表示
                        JOptionPane.showMessageDialog(null, '完了しました。', '処理完了', JOptionPane.INFORMATION_MESSAGE)
                    })
                    button(id: 'executeButton', text: 'SQL実行->CSV出力',actionPerformed: {
                        String sqlQuery = swingBuilder.sqlTextField.text
                        String outputPath = swingBuilder.outputFile.text
                        String outputEncoding = swingBuilder.outputEncoding.text
                        db2csv(sqlQuery, outputPath, outputEncoding)
                        // 処理完了メッセージを表示
                        JOptionPane.showMessageDialog(null, '完了しました。', '処理完了', JOptionPane.INFORMATION_MESSAGE)
                    })
                    button(id: 'execAll', text: '一括実行',actionPerformed: {
                        startProcessing(tables,outputs,customQuery)
                        // 処理完了メッセージを表示
                        JOptionPane.showMessageDialog(null, '完了しました。', '処理完了', JOptionPane.INFORMATION_MESSAGE)
                    })
                    button(text: '閉じる', actionPerformed: {
                        int response = JOptionPane.showConfirmDialog(null, '本当に終了しますか？', '確認', JOptionPane.YES_NO_OPTION)
                        if (response == JOptionPane.YES_OPTION) {
                            System.exit(0)
                        }
                    })
                }
            }
        }
        frame.setLocationRelativeTo(null)
    }
} else {
    startProcessing(tables,outputs,customQuery)
}

def log(def message) {
    new File('logs').mkdir()
    new File("logs/${ new Date().format('yyyy-MM-dd')}.log").withWriterAppend { out ->
        out.println "${new Date().format('yyyy-MM-dd HH:mm:ss')} ${message}"
        println "${new Date().format('yyyy-MM-dd HH:mm:ss')} ${message}"
    }
}


def startProcessing(Map tables, Map outputs,String customQuery) {
    try {
        // CSVをDBに取込
        importFileToDb(tables,customQuery)
    } catch (Throwable e) {
        log e.toString()
        System.exit(9)
    }

    // DBからCSVへエクスポート
    db2file(outputs)
}

def executeCustomSql(String query) {
    def conn = DriverManager.getConnection('jdbc:duckdb:temp/db')
    def db = Sql.newInstance(conn)
    db.execute "PRAGMA memory_limit='-1'"
    try {
        query.split(";").each(){q ->
            log "Executing Custom SQL: ${q}"
            if(q.trim().size()>0){
                db.execute(q)
            }
        }
    } catch (Throwable e) {
        log e.toString()
    }
}

def importFileToDb(Map tables,String customQuery) {
    def conn = DriverManager.getConnection('jdbc:duckdb:temp/db')
    def db = Sql.newInstance(conn)
    db.execute "PRAGMA memory_limit='-1'"
    tables.each { table, param ->
        if (param.type == 'csv') {
            csv2table(db, table, param)
        } else if (param.type == 'sam') {
            sam2table(db, table, param)
        } else if (param.type == 'excel') {
            excel2table(db, table, param)
        }
    }
    // 任意のSQLを実行
    executeCustomSql(customQuery)
}

def csv2table(Sql db, String table, Map parameter) {
    def file_path = new File(parameter.path).absolutePath.replaceAll('\\\\', '/')
    def sql

    if (parameter.columns && parameter.type=='csv') {
        // ヘッダーを含まないCSVファイルの場合
        String columns1 = parameter.columns.collect { it }.join(" VARCHAR, ") + " VARCHAR"
        String columns2 = parameter.columns.collect { it }.join(" , ")
        sql = """CREATE TABLE ${table} (${columns1})"""
        log(sql.toString())
        db.execute "DROP TABLE IF EXISTS ${table}".toString()
        db.execute sql.toString()

        // CSVファイルのデータを挿入
        sql = """COPY ${table} (${columns2}) FROM '${file_path}' (HEADER FALSE, DELIMITER ',', QUOTE '"')"""
    } else {
        // ヘッダーを含むCSVファイルの場合
        sql = """CREATE TABLE ${table} AS
            SELECT * FROM read_csv_auto('${file_path}', all_varchar=true)
        """
    }

    log(sql.toString())
    db.execute sql.toString()
    log("${table} imported:" + db.firstRow("SELECT COUNT(*) RECORD_COUNT FROM ${table}".toString()))
    db.eachRow("DESCRIBE ${table}".toString()) { row ->
        log(row.toString())
    }
}

def sam2table(Sql db, String table, Map parameter) {
    def file_path = new File(parameter.path).absolutePath.replaceAll('\\\\', '/')
    String encode = parameter.encode
    String filter_key = parameter.filter.key
    String filter_value = parameter.filter.value
    String csv_path = file_path + '.csv'
    int record_length = parameter.record_length.toString().toInteger()

    new File(csv_path).withWriter(encode) { out ->
        // ヘッダーの書き込み
        boolean start1 = true
        parameter.columns.each { column ->
            if (start1) {
                start1 = false
            } else {
                out.print ','
            }
            out.print "\"${column.name}\""
        }
        out.println ''

        // SAMファイルの読み込みとCSVへの変換
        new File(file_path).withInputStream { stream ->
            byte[] buff = new byte[record_length]
            while (stream.read(buff) != -1) {
                Map row = [:]
                int pos = 0
                parameter.columns.each { column ->
                    int length = column.length
                    String encode2 = column.encode
                    def record = new String(buff, pos, length, encode2).trim()
                    row.put(column.name, record)
                    pos += length
                }
                if (row[filter_key] == filter_value) {
                    boolean start2 = true
                    row.each { k, v ->
                        if (start2) {
                            start2 = false
                        } else {
                            out.print ','
                        }
                        out.print "\"${v}\""
                    }
                    out.println ''
                }
            }
        }
        out.flush()
    }
    parameter.path = csv_path
    parameter.option = "quote='\"'"
    csv2table(db, table, parameter)
    new File(csv_path).delete()
}

def excel2table(Sql db, String table, Map parameter) {
    String filePath = new File(parameter.path).absolutePath.replaceAll('\\\\', '/')
    String sheetName = parameter.sheet
    int startRow = parameter.startRow ?: 0
    int startCol = parameter.startCol ?: 0
    int rowCount = parameter.rowCount ?: 0
    int colCount = parameter.colCount ?: 0
    String csvPath = filePath + ".csv"

    FileInputStream fis = null
    Workbook workbook = null
    try {
        fis = new FileInputStream(filePath)
        workbook = new XSSFWorkbook(fis)
        Sheet sheet = workbook.getSheet(sheetName)
        if (sheet == null) {
            log "Sheet ${sheetName} does not exist in ${filePath}"
            return
        }

        new File(csvPath).withWriter { out ->
            // ヘッダーの書き込み
            List<String> headers = []
            Row headerRow = sheet.getRow(startRow)
            if (headerRow != null) {
                for (int i = startCol; i < startCol + colCount; i++) {
                    Cell cell = headerRow.getCell(i)
                    headers.add(cell?.toString() ?: "")
                }
                out.println(headers.collect { "\"${it}\"" }.join(","))
            }

            // データの書き込み
            for (int r = startRow + 1; r < startRow + 1 + rowCount; r++) {
                Row row = sheet.getRow(r)
                if (row == null) continue

                List<String> rowData = []
                for (int c = startCol; c < startCol + colCount; c++) {
                    Cell cell = row.getCell(c)
                    rowData.add(cell?.toString() ?: "")
                }
                out.println(rowData.collect { "\"${it}\"" }.join(","))
            }
        }
    } catch (Exception e) {
        log "Error reading Excel file: ${e.message}"
    } finally {
        if (workbook != null) {
            workbook.close()
        }
        if (fis != null) {
            fis.close()
        }
    }

    // CSVに変換したファイルをcsv2tableで読み込む
    parameter.path = csvPath
    parameter.option = "quote='\"'"
    csv2table(db, table, parameter)
    new File(csvPath).delete()
}

def db2file(Map outputs) {
    outputs.each(){name,output ->
        println output;
        String outputPath = output.path
        outputPath = outputPath.replaceAll("\\\$DATE\\\$", new Date().format('yyyyMMdd'))
        outputPath = outputPath.replaceAll("\\\$TIME\\\$", new Date().format('HHmmss'))
        String sql = output.query.join(' ')
        String type = output.type;
        if(type=="csv"){
            String outputEncoding = output.encoding
            db2csv(sql,outputPath,outputEncoding)
        }else if(type=="sam"){
            boolean lineSepareted = output.lineSepareted;
            List columns = output.columns
            db2sam(sql,outputPath,lineSepareted,columns)
        }
    }
}
def db2csv(String query, String outputPath, String outputEncoding) {
    def conn = DriverManager.getConnection('jdbc:duckdb:temp/db')
    def db = Sql.newInstance(conn)
    db.execute "PRAGMA memory_limit='-1'"
    long start2 = System.currentTimeMillis()
    log 'Process Start:'
    try {
        String sqlCopy = "COPY (${query}) TO '${outputPath}' (HEADER,ENCODING '${outputEncoding}',QUOTE '')"
        log(sqlCopy)
        db.execute sqlCopy
    } catch (Throwable e) {
        log e.toString()
    }
    long end = System.currentTimeMillis()
    log "Process End:${end - start2} (ms)"
}

def db2sam(String query, String outputPath, boolean lineSeparated, List columns) {
    def conn = DriverManager.getConnection('jdbc:duckdb:temp/db')
    def db = Sql.newInstance(conn)
    long start = System.currentTimeMillis()
    log 'Process db2sam Start:'
    try {
        // 固定長ファイルの書き込み
        new File(outputPath).withOutputStream { outputStream ->
            db.eachRow(query) { row ->
                StringBuilder line = new StringBuilder()
                columns.each { column ->
                    String value = row[column.name]?.toString() ?: ""
                    String encode = column.encode // 各列のエンコーディングを取得
                    String paddedValue = value.padRight(column.length, column.padding.charAt(0).toString()).substring(0, column.length)
                    
                    // エンコーディングを考慮してバイト配列に変換
                    byte[] encodedBytes = paddedValue.getBytes(encode)
                    outputStream.write(encodedBytes)

                    if (lineSeparated) {
                        outputStream.write(System.lineSeparator().getBytes(encode))
                    }
                }
            }
        }
    } catch (Exception e) {
        log "Error writing to fixed length file: ${e.message}"
    } finally {
        long end = System.currentTimeMillis()
        log "Process End:${end - start} (ms)"
    }
}
