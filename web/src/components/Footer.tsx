import React from 'react';
import { makeStyles, Theme, Typography } from '@material-ui/core';
import RouterLink from './RouterLink';

const useStyles = makeStyles((theme: Theme) => ({
  link: {
    textDecoration: 'none',
    color: `${theme.palette.text.primary}`,
  },
  wrapper: {
    display: 'flex',
    flexGrow: 1,
    flexDirection: 'row',
    backgroundColor: `${theme.palette.grey[800]}`,
    position: 'absolute',
    bottom: 0,
    height: '9rem',
    width: '100%',
  },
  section: {
    display: 'flex',
    flexDirection: 'column',
    flexGrow: 1,
    alignItems: 'flex-start',
    padding: `${theme.spacing(2)}px ${theme.spacing(3)}px`,
  },
}));

interface Props {}

const Footer = (props: Props) => {
  const classes = useStyles();
  console.log(props);
  return (
    <footer className={classes.wrapper}>
      <div className={classes.section}>Teletracker &copy; 2019</div>
      <div className={classes.section}>
        <Typography variant="h6">Networks</Typography>
        <RouterLink
          to={'/popular?networks=hbo-go%2Chbo-now'}
          className={classes.link}
        >
          HBO
        </RouterLink>
        <RouterLink to={'/popular?networks=hulu'} className={classes.link}>
          Hulu
        </RouterLink>
        <RouterLink
          to={'/popular?networks=netflix%2Cnetflix-kids'}
          className={classes.link}
        >
          Netflix
        </RouterLink>
      </div>
      <div className={classes.section}>
        <Typography variant="h6">What's Popular</Typography>
        <RouterLink to={'/popular?type=movie'} className={classes.link}>
          Movies
        </RouterLink>
        <RouterLink to={'/popular?type=show'} className={classes.link}>
          TV Shows
        </RouterLink>
      </div>
      <div className={classes.section}>
        <Typography variant="h6">Top Genres</Typography>
        <RouterLink to={'/popular?genres=83'} className={classes.link}>
          Action &amp; Adventure
        </RouterLink>
        <RouterLink to={'/popular?genres=85'} className={classes.link}>
          Comedy
        </RouterLink>
        <RouterLink to={'/popular?genres=88'} className={classes.link}>
          Horror
        </RouterLink>
        <RouterLink to={'/popular?genres=92'} className={classes.link}>
          Horror
        </RouterLink>
      </div>
      <div className={classes.section}>
        <Typography variant="h6">More</Typography>
        <div>About Us</div>
        <div>Contact Us</div>
      </div>
    </footer>
  );
};

export default Footer;
