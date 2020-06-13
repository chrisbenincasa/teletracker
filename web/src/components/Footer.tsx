import React from 'react';
import { Hidden, makeStyles, Theme, Typography } from '@material-ui/core';
// import RouterLink from './RouterLink';
import RouterLink from 'next/link';
import moment from 'moment';

const useStyles = makeStyles((theme: Theme) => ({
  link: {
    textDecoration: 'none',
    color: theme.palette.text.primary,
  },
  mobileFooter: {
    display: 'flex',
    flexGrow: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  wrapper: {
    display: 'flex',
    flexDirection: 'row',
    backgroundColor: theme.custom.hover.active,
    position: 'absolute',
    bottom: 0,
    height: '9rem',
    [theme.breakpoints.down('sm')]: {
      height: '3rem',
    },
    width: '100%',
    marginTop: theme.spacing(2),
    alignSelf: 'flex-end',
    zIndex: theme.zIndex.appBar - 2, // Allows QuickSearch to appear over it in smaller windows
  },
  section: {
    display: 'flex',
    flexDirection: 'column',
    flexGrow: 1,
    alignItems: 'flex-start',
    padding: theme.spacing(2, 3),
  },
}));

const Footer = () => {
  const classes = useStyles();
  const year = moment().format('YYYY');

  return (
    <footer className={classes.wrapper}>
      <Hidden only={['xs', 'sm']}>
        <div className={classes.section}>Teletracker &copy; {year}</div>
        <div className={classes.section}>
          <Typography variant="h6">Networks</Typography>
          <RouterLink href="/popular?networks=hbo-go" passHref>
            <a className={classes.link}>HBO</a>
          </RouterLink>
          <RouterLink href={'/popular?networks=hulu'}>
            <a className={classes.link}>Hulu</a>
          </RouterLink>
          <RouterLink href={'/popular?networks=netflix'}>
            <a className={classes.link}>Netflix</a>
          </RouterLink>
        </div>
        <div className={classes.section}>
          <Typography variant="h6">What's Popular</Typography>
          <RouterLink href={'/popular?type=movie'}>
            <a className={classes.link}>Movies</a>
          </RouterLink>
          <RouterLink href={'/popular?type=show'}>
            <a className={classes.link}>TV Shows</a>
          </RouterLink>
        </div>
        <div className={classes.section}>
          <Typography variant="h6">Top Genres</Typography>
          <RouterLink href={'/popular?genres=83'}>
            <a className={classes.link}>Action &amp; Adventure</a>
          </RouterLink>
          <RouterLink href={'/popular?genres=85'}>
            <a className={classes.link}>Comedy</a>
          </RouterLink>
          <RouterLink href={'/popular?genres=88'}>
            <a className={classes.link}>Drama</a>
          </RouterLink>
          <RouterLink href={'/popular?genres=92'}>
            <a className={classes.link}>Horror</a>
          </RouterLink>
        </div>
        <div className={classes.section}>
          <Typography variant="h6">More</Typography>
          <div>About Us</div>
          <div>Contact Us</div>
        </div>
      </Hidden>
      <Hidden mdUp>
        <div className={classes.mobileFooter}>Teletracker &copy; {year}</div>
      </Hidden>
    </footer>
  );
};

export default Footer;
