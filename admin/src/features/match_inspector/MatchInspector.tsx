import {
  Button,
  Card,
  CardActionArea,
  CardContent,
  CardMedia,
  Chip,
  createStyles,
  Grid,
  Icon,
  makeStyles,
  Paper,
  Typography,
} from '@material-ui/core';
import { useDispatch } from 'react-redux';
import React, { ChangeEvent, useState } from 'react';
import InfiniteScroll from 'react-infinite-scroller';
import { RouteComponentProps } from '@reach/router';

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
    navigationType: {
      marginBottom: theme.spacing(2),
    },
    totalItemsWrapper: {
      textAlign: 'center',
      marginBottom: theme.spacing(2),
    },
    cardBadgeAlign: {
      transform: 'scale(1.5) translate(-15%, -15%)',
    },
  }),
);

type MatchItem = {
  esItem: EsMatchItem;
  scrapedItem: ScrapedMatchItem;
};

type EsMatchItem = {
  description: string;
  external_ids: string[];
  id: string;
  images?: {
    provider_id: number;
    provider_shortname: string;
    id: string;
    image_type: string;
  }[];
  original_title: string;
  popularity: number;
  release_date: string;
  slug?: string;
  title: string;
  type: string;
};

type ScrapedMatchItem = {
  cast: object[];
  crew: object[];
  description: string;
  externalId: string;
  itemType: string;
  network: string;
  title: string;
  releaseYear: number;
};

export default function MatchInspector(props: RouteComponentProps) {
  const classes = useStyles();
  const dispatch = useDispatch();

  const [items, setItems] = useState<MatchItem[]>([]);
  const [visibleItems, setVisibleItems] = useState<MatchItem[]>([]);

  const cards = visibleItems.map((representativeItem) => {
    const poster = (representativeItem.esItem.images || []).find(
      (image) => image.image_type === 'poster',
    );

    const ttLink = `https://qa.teletracker.tv/${representativeItem.esItem.type}s/${representativeItem.esItem.id}`;
    let scrapedPoster;
    if (representativeItem.scrapedItem.network === 'hbo') {
      let parts = representativeItem.scrapedItem.externalId.split(':');
      let image_id = parts[parts.length - 1];
      scrapedPoster = `https://artist.api.lv3.cdn.hbo.com/images/${image_id}/tile?size=1280x720&compression=low&protection=false&scaleDownToFit=false`;
    }

    let scrapedItemUrl;
    if (representativeItem.scrapedItem.network === 'hbo') {
      scrapedItemUrl = `https://play.hbogo.com/feature/${representativeItem.scrapedItem.externalId}`;
    }

    // let networkSet = new Set<string>();
    // items.forEach((item) => {
    //   (item.availability || [])
    //     .map((av) => av.network_name)
    //     .forEach((network) => (network ? networkSet.add(network) : undefined));
    // });
    // const networks = Array.from(networkSet.keys()).join(', ');

    // const badgeContent = (
    //   <Tooltip title={networks}>
    //     <span>{items.length}</span>
    //   </Tooltip>
    // );

    return (
      <Grid item xs={4} key={representativeItem.esItem.id}>
        <Paper elevation={3} className={classes.paper}>
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
                    {representativeItem.esItem.title}
                  </Typography>
                  {representativeItem.esItem.type && (
                    <Chip
                      label={representativeItem.esItem.type}
                      className={classes.chip}
                    />
                  )}
                  {representativeItem?.esItem?.release_date && (
                    <Chip
                      label={representativeItem?.esItem?.release_date?.substring(
                        0,
                        4,
                      )}
                      className={classes.chip}
                    />
                  )}
                  <Typography
                    variant="body2"
                    color="textSecondary"
                    component="p"
                    className={classes.cardDescription}
                  >
                    {representativeItem.esItem?.description}
                  </Typography>
                </CardContent>
              </CardActionArea>
            </Card>
            <Card className={classes.card}>
              <CardActionArea
                component="a"
                href={scrapedItemUrl}
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
                    {representativeItem.scrapedItem.title}
                  </Typography>
                  {representativeItem.scrapedItem.itemType && (
                    <Chip
                      label={representativeItem.scrapedItem.itemType}
                      className={classes.chip}
                    />
                  )}
                  {representativeItem.scrapedItem.releaseYear && (
                    <Chip
                      label={representativeItem.scrapedItem.releaseYear}
                      className={classes.chip}
                    />
                  )}
                  {representativeItem.scrapedItem.network && (
                    <Chip
                      label={representativeItem.scrapedItem.network}
                      className={classes.chip}
                    />
                  )}
                  <Typography
                    variant="body2"
                    color="textSecondary"
                    component="p"
                    className={classes.cardDescription}
                  >
                    {representativeItem.scrapedItem.description}
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
              // onClick={() => markItemsAsNonMatch(items.map((i) => i.id))}
            >
              Not a Match
            </Button>
            <Button
              size="small"
              color="primary"
              variant="contained"
              fullWidth
              className={classes.button}
              // onClick={() => markItemsAsMatch(items.map((i) => i.id))}
            >
              Hooray, a Match!
            </Button>
          </div>
        </Paper>
      </Grid>
    );
  });

  const makeMoreItemsVisible = (howMany: number) => {
    setVisibleItems((prev) => {
      return [
        ...prev,
        ...items.slice(
          prev.length,
          Math.min(prev.length + howMany, items.length),
        ),
      ];
    });
  };

  const handleUploadFile = (event: ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files.length > 0) {
      event.target.files
        .item(0)
        ?.text()
        .then((result) => {
          let results = result
            .split('\n')
            .filter((str) => str.length > 0)
            .map((str) => {
              let parsed = JSON.parse(str);
              return {
                esItem: parsed.potential,
                scrapedItem: parsed.scraped,
              } as MatchItem;
            });
          setItems(results);
          setVisibleItems([...results.slice(0, 10)]);
        });
    }
  };

  return (
    <React.Fragment>
      <input type="file" name="file" onChange={handleUploadFile} />
      <div className={classes.totalItemsWrapper}>
        {/*<Typography*/}
        {/*  variant="body2"*/}
        {/*  color="textSecondary"*/}
        {/*  component="p"*/}
        {/*>{`Showing ${items.length} of ${totalItems} total items`}</Typography>*/}
      </div>
      <InfiniteScroll
        pageStart={0}
        loadMore={() => makeMoreItemsVisible(10)}
        hasMore={
          visibleItems.length > 0 &&
          items.length > 0 &&
          visibleItems.length < items.length
        }
        useWindow
        threshold={300}
      >
        <Grid container spacing={3}>
          <div className={classes.wrapper}>{cards}</div>
        </Grid>
      </InfiniteScroll>
    </React.Fragment>
  );
}
