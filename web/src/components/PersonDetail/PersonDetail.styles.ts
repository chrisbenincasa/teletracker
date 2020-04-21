import { createStyles, makeStyles, Theme } from '@material-ui/core';

const classes = makeStyles((theme: Theme) =>
  createStyles({
    backdrop: {
      width: '100%',
      height: '100%',
      display: 'flex',
      zIndex: 1,
    },
    backdropContainer: {
      height: 'auto',
      overflow: 'hidden',
      top: 0,
      width: '100%',
      position: 'fixed',
      [theme.breakpoints.down('sm')]: {
        height: '100%',
      },
    },
    backdropGradient: {
      position: 'absolute',
      top: 0,
      width: '100%',
      height: '100%',
      backgroundColor: theme.custom.backdrop.backgroundColor,
      backgroundImage: theme.custom.backdrop.backgroundImage,
    },
    descriptionContainer: {
      display: 'flex',
      flexDirection: 'column',
      marginBottom: theme.spacing(1),
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      marginBottom: theme.spacing(1),
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
    filterSortContainer: {
      marginBottom: theme.spacing(1),
      flexGrow: 1,
      [theme.breakpoints.up('sm')]: {
        display: 'flex',
      },
    },
    genre: {
      margin: theme.spacing(1),
    },
    header: {
      padding: theme.spacing(1, 0),
      fontWeight: 700,
    },
    leftContainer: {
      display: 'flex',
      flexDirection: 'column',
      position: 'relative',
      [theme.breakpoints.up('md')]: {
        position: 'sticky',
        top: 75,
        height: 475,
      },
    },
    listHeader: {
      marginTop: theme.spacing(1),
      display: 'flex',
      flex: '1 0 auto',
      alignItems: 'center',
    },
    listNameContainer: {
      display: 'flex',
      flex: '1 0 auto',
    },
    personCTA: {
      width: '100%',
      [theme.breakpoints.down('sm')]: {
        width: '80%',
      },
    },
    personInformationContainer: {
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: '#fff',
      flexDirection: 'column',
      position: 'relative',
      [theme.breakpoints.up('sm')]: {
        marginLeft: theme.spacing(3),
      },
      marginBottom: theme.spacing(2),
    },
    personDetailContainer: {
      margin: theme.spacing(3),
      display: 'flex',
      flex: '1 1 auto',
      color: '#fff',
      [theme.breakpoints.down('sm')]: {
        flexDirection: 'column',
        margin: theme.spacing(1),
      },
    },
    posterContainer: {
      margin: '0 auto',
      width: '50%',
      position: 'relative',
      [theme.breakpoints.up('sm')]: {
        width: 250,
      },
    },
    settings: {
      display: 'flex',
      alignSelf: 'flex-end',
    },
    titleContainer: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'flex-start',
      width: '100%',
      marginBottom: theme.spacing(1),
      zIndex: theme.zIndex.mobileStepper,
      [theme.breakpoints.down('sm')]: {
        textAlign: 'center',
        alignItems: 'center',
        margin: theme.spacing(1, 0, 2, 0),
      },
    },
    trackingButton: {
      marginTop: theme.spacing(1),
    },
    loadingCircle: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      height: '100%',
    },
    fin: {
      fontStyle: 'italic',
      textAlign: 'center',
      margin: theme.spacing(6),
    },
  }),
);

export default classes;
