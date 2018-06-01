import { GlobalConfig } from './Config';
import { Logger, createLogger, transports, format } from 'winston';
const { combine, timestamp, label, prettyPrint } = format;

const logger: Logger = createLogger({
    level: GlobalConfig.logging.level
});

if (process.env.NODE_ENV !== 'production') {
    logger.add(new transports.Console({
        format: combine(
            format.colorize(),
            format.simple()
        )
    }));
}

export default logger;