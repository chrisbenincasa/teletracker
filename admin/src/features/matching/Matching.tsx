import React, { useEffect } from 'react';
import { RouteComponentProps } from '@reach/router';
import { useDispatch, useSelector } from 'react-redux';
import { fetchMatches, selectMatchItems } from './matchingSlice';
import {
  Button,
  Card,
  CardActionArea,
  CardActions,
  CardContent,
  CardMedia,
  Chip,
  Grid,
  createStyles,
  Icon,
  makeStyles,
  Paper,
  Typography,
} from '@material-ui/core';
import { ScrapeItemType } from '../../types';

const useStyles = makeStyles((theme) =>
  createStyles({
    chip: {
      margin: theme.spacing(0.5, 0.5, 0.5, 0),
    },
    wrapper: {
      display: 'flex',
      flexWrap: 'wrap',
    },
    buttonWrapper: {
      flexDirection: 'row',
      display: 'flex',
      flexGrow: 1,
      height: 35,
    },
    button: {
      margin: 4,
    },
    card: {
      width: '50%',
      margin: 4,
    },
    cardDescription: {
      height: 'auto',
      overflow: 'scroll',
    },
    cardWrapper: {
      display: 'flex',
      flexGrow: 1,
      height: 600,
    },
    dataType: {
      color: '#fff',
      backgroundColor: '#3f51b5',
      marginLeft: -16,
      paddingLeft: 8,
    },
    fallbackImageWrapper: {
      display: 'flex',
      color: theme.palette.grey[500],
      backgroundColor: theme.palette.grey[300],
      fontSize: '10rem',
      width: '100%',
      objectFit: 'cover',
      height: 275,
    },
    fallbackImageIcon: {
      alignSelf: 'center',
      margin: '0 auto',
      display: 'inline-block',
    },
    paper: {
      display: 'flex',
      justifyContent: 'space-around',
      flex: 1,
      margin: 8,
      padding: 8,
      flexWrap: 'wrap',
      height: 650,
    },
  }),
);

type Props = RouteComponentProps;

export default function Matching(props: Props) {
  const classes = useStyles();
  const dispatch = useDispatch();
  const items = useSelector(selectMatchItems);

  useEffect(() => {
    dispatch(fetchMatches());
  }, []);

  const cards = items.slice(0, 10).map((item) => {
    const poster = (item.potential.images || []).find(
      (image) => image.image_type === 'poster',
    );

    const ttLink = `https://qa.teletracker.tv/${item.potential.type}s/${item.potential.id}`;
    const scrapedPoster = item.scraped.item.posterImageUrl;

    return (
      <Grid item xs={4}>
        <Paper elevation={3} className={classes.paper} key={item.id}>
          <div className={classes.cardWrapper}>
            <Card className={classes.card}>
              <CardActionArea component="a" href={ttLink} target="_blank">
                <CardMedia
                  component="img"
                  height="275"
                  image={
                    poster?.id
                      ? `https://image.tmdb.org/t/p/w342${poster!.id}`
                      : ''
                  }
                />
                <CardContent>
                  <Typography
                    gutterBottom
                    variant="h5"
                    component="h2"
                    className={classes.dataType}
                  >
                    Potential
                  </Typography>
                  <Typography gutterBottom variant="h5" component="h2">
                    {item.potential.title}
                  </Typography>
                  <Chip label={item.potential.type} className={classes.chip} />
                  <Chip
                    label={item?.potential?.release_date?.substring(0, 4)}
                    className={classes.chip}
                  />
                </CardContent>
              </CardActionArea>
            </Card>
            <Card className={classes.card}>
              <CardActionArea
                component="a"
                href={item.scraped.item.url}
                target="_blank"
              >
                {scrapedPoster ? (
                  <CardMedia
                    component="img"
                    height="275"
                    image={scrapedPoster}
                  />
                ) : (
                  <div className={classes.fallbackImageWrapper}>
                    <Icon
                      className={classes.fallbackImageIcon}
                      fontSize="inherit"
                    >
                      broken_image
                    </Icon>
                  </div>
                )}

                <CardContent>
                  <Typography
                    gutterBottom
                    variant="h5"
                    component="h2"
                    className={classes.dataType}
                  >
                    Scraped
                  </Typography>
                  <Typography gutterBottom variant="h5" component="h2">
                    {item.scraped.item.title}
                  </Typography>
                  <Chip label={item.scraped.item.itemType} />
                  <Chip label={item.scraped.item.releaseYear} />
                  <Typography
                    variant="body2"
                    color="textSecondary"
                    component="p"
                    className={classes.cardDescription}
                  >
                    {item.scraped.item.description}
                  </Typography>
                </CardContent>
              </CardActionArea>
            </Card>
          </div>
          <div className={classes.buttonWrapper}>
            <Button
              size="small"
              color="secondary"
              variant="contained"
              fullWidth
              className={classes.button}
            >
              Not a Match
            </Button>
            <Button
              size="small"
              color="primary"
              variant="contained"
              fullWidth
              className={classes.button}
            >
              Hooray, a Match!
            </Button>
          </div>
        </Paper>
      </Grid>
    );
  });

  return (
    <Grid container spacing={3}>
      <div className={classes.wrapper}>{cards}</div>
    </Grid>
  );
}
