import java.sql.DriverManager
import groovy.sql.*
import groovy.json.*
import java.nio.file.*
import groovy.swing.SwingBuilder
import javax.swing.*
import java.awt.BorderLayout
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook

public class DataFileMergeTool {
    int WIDTH = 800
    static String dbClass
    static String dbUrl
    static String dbInitSql

    public static void main(String[] args) {
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
        def json = new JsonSlurper().parse(new File(config), "UTF-8")

        // JSONから必要なデータを抽出
        def title = json.title ?: 'DataFile Merge Tool'
        def description = json.description ?: 'This tool is for merging Data files.'
        dbClass = json.database.class
        dbUrl = json.database.url
        dbInitSql = json.database.init
        log "${dbClass}/[${dbUrl}]"

        Map tables = [:]
        json.input.each { t ->
            log "Input:${t.key}"
            tables.put(t.key, t.value)
        }
        Map outputs = [:]
        json.output.each { t ->
            log "Output:${t.key}"
            outputs.put(t.key, t.value)
        }

        String customQuery = ""
        json.customQuery.each { line ->
            log "CustomQuery:${line}"
            customQuery += line
            customQuery += ' '
        }

        new File('temp/db').delete()
        new File('temp').mkdir()
        Class.forName(dbClass)

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
                        panel() {
                            gridLayout(columns: 1, rows: 1)
                            textArea(id: 'sqlTextField', rows: 5, columns: 40)
                        }
                    }
                    panel(preferredSize: [WIDTH, 60], constraints: BorderLayout.SOUTH) {
                        gridLayout(columns: 1, rows: 2)
                        panel(preferredSize: [WIDTH, 30]) {
                            gridLayout(columns: 3, rows: 1)
                            label('Output File/Encoding: ')
                            textField(id: 'outputFile', text: "sample-out.txt", columns: 400)
                            textField(id: 'outputEncoding', text: "UTF-8", columns: 400)
                        }
                        panel(preferredSize: [WIDTH, 30]) {
                            gridLayout(columns: 4, rows: 1)
                            // ファイル取込ボタン
                            button(text: 'ファイル取込', actionPerformed: {
                                tables.each { key, value -> value.path = swingBuilder."${key}File".text }
                                importFileToDb(tables, customQuery)
                                // 処理完了メッセージを表示
                                JOptionPane.showMessageDialog(null, '完了しました。', '処理完了', JOptionPane.INFORMATION_MESSAGE)
                            })
                            button(id: 'executeButton', text: 'SQL実行->CSV出力', actionPerformed: {
                                String sqlQuery = swingBuilder.sqlTextField.text
                                String outputPath = swingBuilder.outputFile.text
                                String outputEncoding = swingBuilder.outputEncoding.text
                                db2csv(sqlQuery, outputPath, outputEncoding, [:])
                                // 処理完了メッセージを表示
                                JOptionPane.showMessageDialog(null, '完了しました。', '処理完了', JOptionPane.INFORMATION_MESSAGE)
                            })
                            button(id: 'execAll', text: '一括実行', actionPerformed: {
                                startProcessing(tables, outputs, customQuery)
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
            startProcessing(tables, outputs, customQuery)
        }
    }

    static def log(def message) {
        new File('logs').mkdir()
        new File("logs/${new Date().format('yyyy-MM-dd')}.log").withWriterAppend { out ->
            out.println "${new Date().format('yyyy-MM-dd HH:mm:ss')} ${message}"
            println "${new Date().format('yyyy-MM-dd HH:mm:ss')} ${message}"
        }
    }

    static def startProcessing(Map tables, Map outputs, String customQuery) {
        try {
            // CSVをDBに取込
            importFileToDb(tables, customQuery)
        } catch (Throwable e) {
            log e.toString()
            System.exit(9)
        }

        // DBからCSVへエクスポート
        db2file(outputs)
    }

    static def executeCustomSql(String query) {
        def conn = DriverManager.getConnection(dbUrl)
        def db = Sql.newInstance(conn)
        db.execute dbInitSql
        try {
            query.split(";").each { q ->
                log "Executing Custom SQL: ${q}"
                if (q.trim().size() > 0) {
                    db.execute(q)
                }
            }
        } catch (Throwable e) {
            log e.toString()
        }
    }

    static def importFileToDb(Map tables, String customQuery) {
        def conn = DriverManager.getConnection(dbUrl)
        def db = Sql.newInstance(conn)
        db.execute dbInitSql
        tables.each { table, param ->
            db.execute "DROP TABLE IF EXISTS ${table}".toString()
            if (param.containsKey("folder") && param.folder != null) {
                new File(param.folder).eachFile { file ->
                    if (param.filter && file.name ==~ param.filter) {
                        if (param.type == 'csv') {
                            csv2table(db, table, file.absolutePath, param)
                        } else if (param.type == 'sam') {
                            sam2table(db, table, file.absolutePath, param)
                        } else if (param.type == 'excel') {
                            excel2table(db, table, file.absolutePath, param)
                        }
                    }
                }
            } else {
                if (param.type == 'csv') {
                    csv2table(db, table, param.path, param)
                } else if (param.type == 'sam') {
                    sam2table(db, table, param.path, param)
                } else if (param.type == 'excel') {
                    excel2table(db, table, param.path, param)
                }
            }
        }
        // 任意のSQLを実行
        executeCustomSql(customQuery)
    }

    static def csv2table(Sql db, String table, String filePath, Map parameter) {
        log "csv2table ${table} ${filePath} Start."
        def file_path = new File(filePath).absolutePath.replaceAll('\\\\', '/')
        // CSVファイルの1行目を読み取って列定義を取得
        def encoding = parameter.encode ?: 'UTF-8'
        def headerLine = new File(file_path).withReader(encoding) { reader ->
            return reader.readLine()
        }
        def columnNames = []
        // パラメータにある場合はそちらを優先する
        if (parameter.columns && parameter.type == 'csv') {
            columnNames = parameter.columns.collect { it }
        } else {
            columnNames = headerLine.split(",").collect { it }
        }
        String columns1 = "\"" + columnNames.collect { it.trim().replaceAll("\"", "").replaceAll("\\)", "_").replaceAll("\\(", "_") }.join("\" VARCHAR, \"") + "\" VARCHAR"
        String columns2 = "\"" + columnNames.collect { it.trim().replaceAll("\"", "").replaceAll("\\)", "_").replaceAll("\\(", "_") }.join(" \",\" ") + "\""
        // テーブルの存在を確認
        def tableExists = db.firstRow("SELECT COUNT(*) AS count FROM information_schema.tables WHERE table_name = '${table}'".toString())?.count > 0
        if (!tableExists) {
            db.execute """CREATE TABLE IF NOT EXISTS ${table} (${columns1})""".toString()
        }
        // CSVファイルのデータを挿入
        // db.execute """COPY ${table} FROM '${file_path}' (HEADER TRUE,null_padding true)""".toString()
        db.execute """ INSERT INTO ${table} SELECT * FROM CSVREAD('${file_path}')""".toString()
        log("${table} imported:" + db.firstRow("SELECT COUNT(*) RECORD_COUNT FROM ${table}".toString()))
    }

    static def sam2table(Sql db, String table, String filePath, Map parameter) {
        log "sam2table ${table} ${filePath}"
        def file_path = new File(filePath).absolutePath.replaceAll('\\\\', '/')
        String filter_key = parameter.filter.key
        String filter_value = parameter.filter.value
        String csv_path = file_path + '.csv'
        int record_length = parameter.record_length.toString().toInteger()

        new File(csv_path).withWriter("UTF-8") { out ->
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

        parameter.option = "quote='\"'"
        csv2table(db, table, csv_path, parameter)
        new File(csv_path).delete()
    }

    static def excel2table(Sql db, String table, String filePath, Map parameter) {
        filePath = new File(filePath).absolutePath.replaceAll('\\\\', '/')
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
        parameter.option = "quote='\"'"
        csv2table(db, table, csvPath, parameter)
        new File(csvPath).delete()
    }

    static def db2file(Map outputs) {
        outputs.each { name, output ->
            String outputPath = output.path
            String outputEncoding = output.encoding
            outputPath = outputPath.replaceAll("\\\$DATE\\\$", new Date().format('yyyyMMdd'))
            outputPath = outputPath.replaceAll("\\\$TIME\\\$", new Date().format('HHmmss'))
            String sql = output.query.join(' ')
            String type = output.type
            if (type == "csv") {
                db2csv(sql, outputPath, outputEncoding, output)
            } else if (type == "sam") {
                db2sam(sql, outputPath, outputEncoding, output)
            }
        }
    }

    static def db2csv(String query, String outputPath, String outputEncoding, Map parameter) {
        int WRITE_BLOCK_SZ = 10000
        def conn = DriverManager.getConnection(dbUrl)
        def db = Sql.newInstance(conn)
        db.execute dbInitSql
        long start2 = System.currentTimeMillis()
        log 'Process Start:'
        long record_count = 1
        try {
            new File(outputPath).withWriter(outputEncoding) { out ->
                if (parameter.containsKey("header") || parameter.containsKey("trailer") || parameter.containsKey("footer")) {
                    if (parameter.containsKey("header")) {
                        def columns_header = db.rows(parameter.header)[0].keySet()
                        db.eachRow(parameter.header) { row ->
                            out.println "\"" + columns_header.collect {row[it] }.join('\",\"') + "\""
                        }
                    }
                    def columns_data = db.rows(query)[0].keySet()
                    db.eachRow(query) { row ->
                        out.println "\"" + columns_data.collect {row[it] }.join('\",\"') + "\""
                        if (record_count++ % WRITE_BLOCK_SZ == 0) { out.flush() }
                    }
                    if (parameter.containsKey("trailer")) {
                        def columns_trailer = db.rows(parameter.trailer)[0].keySet()
                        db.eachRow(parameter.trailer) { row ->
                            out.println "\"" + columns_trailer.collect {row[it] }.join('\",\"') + "\""
                        }
                    }
                    if (parameter.containsKey("footer")) {
                        def columns_footer = db.rows(parameter.trailer)[0].keySet()
                        db.eachRow(parameter.footer) { row ->
                            out.println "\"" + columns_footer.collect { row[it] }.join('\",\"') + "\""
                        }
                    }
                } else {
                    String query_header = query + " LIMIT 1"
                    def columns_data = db.rows(query_header)[0].keySet()
                    out.println("\"" + columns_data.join('\",\"') + "\"")
                    db.eachRow(query) { row ->
                        out.println columns_data.collect { row[it] }.join(',')
                        if (record_count++ % WRITE_BLOCK_SZ == 0) { out.flush() }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace()
            log e.toString()
        }
        long end = System.currentTimeMillis()
        log "Process End:${end - start2} (ms)"
    }

    static def db2sam(String query, String outputPath, String outputEncoding, Map output) {
        def conn = DriverManager.getConnection(dbUrl)
        def db = Sql.newInstance(conn)
        db.execute dbInitSql
        long start = System.currentTimeMillis()
        log 'Process db2sam Start:'
        try {
            // 固定長ファイルの書き込み
            new File(outputPath).withOutputStream { outputStream ->
                if (output.containsKey("header")) {
                    String sql = output.header.query.join(' ')
                    write2stream(outputStream, db, sql, output.header.columns, output.lineSeparated)
                }
                write2stream(outputStream, db, query, output.columns, output.lineSeparated)
                if (output.containsKey("trailer")) {
                    String sql = output.trailer.query.join(' ')
                    write2stream(outputStream, db, sql, output.trailer.columns, output.lineSeparated)
                }
                if (output.containsKey("footer")) {
                    String sql = output.footer.query.join(' ')
                    write2stream(outputStream, db, sql, output.footer.columns, output.lineSeparated)
                }
            }
        } catch (Exception e) {
            log "Error writing to fixed length file: ${e.message}"
        } finally {
            long end = System.currentTimeMillis()
            log "Process End:${end - start} (ms)"
        }
    }

    static def write2stream(OutputStream stream, Sql db, String query, List columns, boolean lineSeparated) {
        db.eachRow(query) { row ->
            columns.each { column ->
                String value = row[column.name]?.toString() ?: ""
                String encode = column.encode // 各列のエンコーディングを取得
                String paddedValue = value.padRight(column.length, column.padding.charAt(0).toString()).substring(0, column.length)
                // エンコーディングを考慮してバイト配列に変換
                byte[] encodedBytes = paddedValue.getBytes(encode)
                stream.write(encodedBytes)
            }
            if (lineSeparated) {
                stream.write(System.lineSeparator().getBytes())
            }
        }
    }
}
